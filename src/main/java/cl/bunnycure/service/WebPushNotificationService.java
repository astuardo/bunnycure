package cl.bunnycure.service;

import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.WebPushSubscription;
import cl.bunnycure.domain.repository.WebPushSubscriptionRepository;
import cl.bunnycure.web.dto.WebPushSubscriptionRequestDto;
import cl.bunnycure.web.dto.WebPushSubscriptionResponseDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebPushNotificationService {

    private final WebPushSubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;
    private final AppSettingsService appSettingsService;

    @Value("${bunnycure.webpush.enabled:true}")
    private boolean webPushEnabled;

    @Value("${bunnycure.webpush.subject:mailto:contacto@bunnycure.cl}")
    private String webPushSubject;

    @Value("${bunnycure.webpush.public-key:}")
    private String webPushPublicKey;

    @Value("${bunnycure.webpush.private-key:}")
    private String webPushPrivateKey;

    @Transactional
    public WebPushSubscriptionResponseDto saveSubscription(WebPushSubscriptionRequestDto request) {
        WebPushSubscription subscription = subscriptionRepository.findByEndpoint(request.getEndpoint())
                .orElseGet(WebPushSubscription::new);

        subscription.setEndpoint(request.getEndpoint());
        subscription.setP256dh(request.getKeys().getP256dh());
        subscription.setAuth(request.getKeys().getAuth());
        subscription.setActive(true);
        subscription.setLastFailureReason(null);

        WebPushSubscription saved = subscriptionRepository.save(subscription);
        return toDto(saved);
    }

    @Transactional
    public void deactivateSubscription(String endpoint) {
        subscriptionRepository.findByEndpoint(endpoint).ifPresent(subscription -> {
            subscription.setActive(false);
            subscriptionRepository.save(subscription);
        });
    }

    public void sendAdminAppointmentReminder(Appointment appointment, String reminderType) {
        if (!webPushEnabled) {
            log.debug("[WEB-PUSH] Deshabilitado por configuración");
            return;
        }

        if (!isWebPushConfigured()) {
            log.warn("[WEB-PUSH] Falta configuración VAPID. Define WEB_PUSH_PUBLIC_KEY, WEB_PUSH_PRIVATE_KEY y WEB_PUSH_SUBJECT.");
            return;
        }

        List<WebPushSubscription> subscriptions = subscriptionRepository.findByActiveTrue();
        if (subscriptions.isEmpty()) {
            return;
        }

        String payload = buildReminderPayload(appointment, reminderType);
        PushService pushService = buildPushService();
        if (pushService == null) {
            return;
        }

        for (WebPushSubscription subscription : subscriptions) {
            sendToSubscription(pushService, subscription, payload);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDiagnostics() {
        List<WebPushSubscription> allSubscriptions = subscriptionRepository.findAll();
        List<WebPushSubscription> activeSubscriptions = allSubscriptions.stream()
                .filter(WebPushSubscription::isActive)
                .toList();

        List<Map<String, Object>> recentSubscriptions = new ArrayList<>();
        for (WebPushSubscription subscription : activeSubscriptions.stream().limit(5).toList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", subscription.getId());
            item.put("endpoint", abbreviateEndpoint(subscription.getEndpoint()));
            item.put("lastSuccessAt", subscription.getLastSuccessAt());
            item.put("lastFailureAt", subscription.getLastFailureAt());
            item.put("lastFailureReason", subscription.getLastFailureReason());
            recentSubscriptions.add(item);
        }

        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("webPushEnabled", webPushEnabled);
        diagnostics.put("vapidConfigured", isWebPushConfigured());
        diagnostics.put("subject", webPushSubject);
        diagnostics.put("hasPublicKey", webPushPublicKey != null && !webPushPublicKey.isBlank());
        diagnostics.put("hasPrivateKey", webPushPrivateKey != null && !webPushPrivateKey.isBlank());
        diagnostics.put("totalSubscriptions", allSubscriptions.size());
        diagnostics.put("activeSubscriptions", activeSubscriptions.size());
        diagnostics.put("recentActiveSubscriptions", recentSubscriptions);
        return diagnostics;
    }

    private void sendToSubscription(PushService pushService, WebPushSubscription subscription, String payload) {
        try {
            Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dh(),
                    subscription.getAuth(),
                    payload.getBytes(StandardCharsets.UTF_8)
            );

            int statusCode = pushService.send(notification).getStatusLine().getStatusCode();

            if (statusCode == 404 || statusCode == 410) {
                subscription.setActive(false);
                subscription.setLastFailureAt(LocalDateTime.now());
                subscription.setLastFailureReason("Subscription expired/unregistered");
                subscriptionRepository.save(subscription);
                return;
            }

            if (statusCode >= 200 && statusCode < 300) {
                subscription.setLastSuccessAt(LocalDateTime.now());
                subscription.setLastFailureReason(null);
            } else {
                subscription.setLastFailureAt(LocalDateTime.now());
                subscription.setLastFailureReason("Push HTTP status: " + statusCode);
            }
            subscriptionRepository.save(subscription);

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            subscription.setLastFailureAt(LocalDateTime.now());
            subscription.setLastFailureReason("Push interrupted");
            subscriptionRepository.save(subscription);
            log.warn("[WEB-PUSH] Envío interrumpido para endpoint {}", subscription.getEndpoint());
        } catch (JoseException | GeneralSecurityException | IOException | ExecutionException ex) {
            subscription.setLastFailureAt(LocalDateTime.now());
            subscription.setLastFailureReason(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            subscriptionRepository.save(subscription);
            log.warn("[WEB-PUSH] Error enviando push para endpoint {}: {}", subscription.getEndpoint(), ex.getMessage());
        }
    }

    private PushService buildPushService() {
        try {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            PushService pushService = new PushService();
            pushService.setPublicKey(webPushPublicKey);
            pushService.setPrivateKey(webPushPrivateKey);
            pushService.setSubject(webPushSubject);
            return pushService;
        } catch (GeneralSecurityException ex) {
            log.error("[WEB-PUSH] Configuración VAPID inválida: {}", ex.getMessage());
            return null;
        }
    }

    private boolean isWebPushConfigured() {
        return webPushPublicKey != null && !webPushPublicKey.isBlank()
                && webPushPrivateKey != null && !webPushPrivateKey.isBlank()
                && webPushSubject != null && !webPushSubject.isBlank();
    }

    private String buildReminderPayload(Appointment appointment, String reminderType) {
        String customerName = appointment.getCustomer() != null && appointment.getCustomer().getFullName() != null
                ? appointment.getCustomer().getFullName()
                : "Cliente";
        String firstName = customerName.split(" ")[0];
        String serviceName = appointment.getService() != null && appointment.getService().getName() != null     
                ? appointment.getService().getName()
                : "Servicio";
        String appointmentTime = appointment.getAppointmentTime() != null
                ? appointment.getAppointmentTime().toString()
                : "hora por confirmar";
        if (appointmentTime.length() > 5) {
            appointmentTime = appointmentTime.substring(0, 5); // HH:mm format
        }
        String appointmentDateStr = appointment.getAppointmentDate() != null
                ? appointment.getAppointmentDate().toString()
                : "fecha por confirmar";
                
        // Convert YYYY-MM-DD to DD/MM/YYYY for UI consistency
        if (appointment.getAppointmentDate() != null) {
             java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
             appointmentDateStr = appointment.getAppointmentDate().format(formatter);
        }
        
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        if (appointment.getTotalPrice() != null && appointment.getTotalPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
            total = appointment.getTotalPrice();
        } else if (appointment.getService() != null) {
            total = appointment.getService().getPrice();
        }
        
        java.text.NumberFormat clpFormat = java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("es", "CL"));
        String totalPriceStr = clpFormat.format(total);

        // Fetch templates
        String titleTemplate = "2_HOUR".equalsIgnoreCase(reminderType) 
            ? appSettingsService.getNotificationTwoHourTitle() 
            : appSettingsService.getNotificationDefaultTitle();
            
        String bodyTemplate = "2_HOUR".equalsIgnoreCase(reminderType) 
            ? appSettingsService.getNotificationTwoHourBody() 
            : appSettingsService.getNotificationDefaultBody();
            
        // Calculate minutes until (approximation if 2_HOUR)
        long minutesUntil = 120;
        if (appointment.getAppointmentDate() != null && appointment.getAppointmentTime() != null) {
             java.time.LocalDateTime appointmentDateTime = java.time.LocalDateTime.of(appointment.getAppointmentDate(), appointment.getAppointmentTime());
             minutesUntil = java.time.Duration.between(java.time.LocalDateTime.now(), appointmentDateTime).toMinutes();
        }

        String finalTitle = titleTemplate
                .replace("{customerName}", customerName)
                .replace("{firstName}", firstName)
                .replace("{serviceName}", serviceName)
                .replace("{time}", appointmentTime)
                .replace("{date}", appointmentDateStr)
                .replace("{minutesUntil}", String.valueOf(minutesUntil))
                .replace("{hoursUntil}", String.valueOf(minutesUntil / 60))
                .replace("{totalPrice}", totalPriceStr)
                .replace("{businessName}", "BunnyCure");
                
        String finalBody = bodyTemplate
                .replace("{customerName}", customerName)
                .replace("{firstName}", firstName)
                .replace("{serviceName}", serviceName)
                .replace("{time}", appointmentTime)
                .replace("{date}", appointmentDateStr)
                .replace("{minutesUntil}", String.valueOf(minutesUntil))
                .replace("{hoursUntil}", String.valueOf(minutesUntil / 60))
                .replace("{totalPrice}", totalPriceStr)
                .replace("{businessName}", "BunnyCure");

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("title", finalTitle);
        payload.put("body", finalBody);
        payload.put("icon", "/icon-192.png");
        payload.put("badge", "/icon-192.png");
        payload.put("tag", "appointment-" + appointment.getId());
        payload.put("requireInteraction", true);

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("appointmentId", appointment.getId());
        data.put("customerName", customerName);
        data.put("serviceName", serviceName);
        data.put("appointmentDate", appointmentDateStr);
        data.put("appointmentTime", appointmentTime);
        data.put("reminderType", reminderType);
        data.put("url", "/calendar");
        payload.put("data", data);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("[WEB-PUSH] Error serializando payload, usando fallback");
            return "{\"title\":\"Recordatorio\",\"body\":\"Tienes una cita próxima\"}";
        }
    }

    private WebPushSubscriptionResponseDto toDto(WebPushSubscription subscription) {
        return WebPushSubscriptionResponseDto.builder()
                .id(subscription.getId())
                .endpoint(subscription.getEndpoint())
                .active(subscription.isActive())
                .build();
    }

    private String abbreviateEndpoint(String endpoint) {
        if (endpoint == null || endpoint.length() <= 80) {
            return endpoint;
        }
        return endpoint.substring(0, 77) + "...";
    }
}
