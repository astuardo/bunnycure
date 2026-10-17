package cl.bunnycure.service;

import cl.bunnycure.config.WhatsAppConfig;
import cl.bunnycure.domain.model.Appointment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import cl.bunnycure.domain.enums.OutboxMessageType;
import cl.bunnycure.domain.event.WhatsAppFailedMessageEvent;
import cl.bunnycure.domain.model.WhatsAppSendResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio para enviar mensajes mediante WhatsApp Cloud API.
 * Documentación: https://developers.facebook.com/docs/whatsapp/cloud-api
 */
@Slf4j
@Service
public class WhatsAppService {

    private static final String WHATSAPP_API_URL = "https://graph.facebook.com/v22.0";

    private final WhatsAppConfig config;
    private final RestTemplate restTemplate;
    private final NotificationLogService notificationLogService;
    private final ObjectMapper objectMapper;
    private final WhatsAppMessageBuilderService messageBuilder;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private cl.bunnycure.domain.repository.TenantRepository tenantRepository;

    @Autowired(required = false)
    private cl.bunnycure.service.billing.PlanAccessService planAccessService;

    /** Envío omitido porque el plan del salón no incluye WhatsApp (no va a la cola de reintentos). */
    static final String PLAN_BLOCKED_ERROR = "WhatsApp no incluido en el plan del salón";

    private boolean whatsappBlockedByPlan() {
        if (planAccessService == null) {
            return false;
        }
        boolean blocked = !planAccessService.currentTenantHas(cl.bunnycure.domain.enums.PlanFeature.WHATSAPP_AUTOMATION);
        if (blocked) {
            log.info("[WHATSAPP-SKIP] El plan del salón {} no incluye WhatsApp: envío omitido", cl.bunnycure.config.TenantContext.getCurrentTenantId());
        }
        return blocked;
    }

    /**
     * Transporte de WhatsApp Cloud API (texto, plantillas, multimedia, interactivos).
     * Los mensajes de negocio (citas, solicitudes, alertas) están en WhatsAppAppointmentNotifier.
     */
    public WhatsAppService(
            WhatsAppConfig config,
            RestTemplate restTemplate,
            NotificationLogService notificationLogService,
            ObjectMapper objectMapper,
            WhatsAppMessageBuilderService messageBuilder) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.notificationLogService = notificationLogService;
        this.objectMapper = objectMapper;
        this.messageBuilder = messageBuilder;
    }

    public String resolveSalonName(Long tenantId) {
        if (tenantId != null && tenantRepository != null) {
            try {
                String name = tenantRepository.findById(tenantId)
                        .map(cl.bunnycure.domain.model.Tenant::getName)
                        .filter(n -> !n.isBlank())
                        .orElse(null);
                if (name != null) return name;
            } catch (Exception ex) {
                log.debug("[WHATSAPP] Error al obtener nombre de tenant {}: {}", tenantId, ex.getMessage());
            }
        }
        try {
            Long current = cl.bunnycure.config.TenantContext.getCurrentTenantId();
            if (current != null && tenantRepository != null) {
                String currentName = tenantRepository.findById(current)
                        .map(cl.bunnycure.domain.model.Tenant::getName)
                        .filter(n -> !n.isBlank())
                        .orElse(null);
                if (currentName != null) return currentName;
            }
        } catch (Exception ignored) {}
        return config.getBusinessName() != null && !config.getBusinessName().isBlank() ? config.getBusinessName() : "BunnyCure";
    }

    /**
     * Marca un mensaje como leído (Read Receipt - Doble check azul en WhatsApp).
     * Endpoint: POST https://graph.facebook.com/v22.0/{phoneId}/messages
     */
    @Async
    public void markMessageAsRead(String messageId) {
        markMessageAsReadSync(messageId);
    }

    public boolean markMessageAsReadSync(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        if (whatsappBlockedByPlan()) {
            return false;
        }
        if (config.getToken() == null || config.getToken().isBlank() || config.getPhoneId() == null || config.getPhoneId().isBlank()) {
            return false;
        }

        try {
            String url = String.format("%s/%s/messages", WHATSAPP_API_URL, config.getPhoneId());

            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("status", "read");
            payload.put("message_id", messageId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getToken());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            boolean success = response.getStatusCode().is2xxSuccessful();
            if (success) {
                log.debug("[WHATSAPP] ✅ Mensaje marcado como leído en Meta. WAMID: {}", messageId);
            }
            return success;
        } catch (Exception ex) {
            log.debug("[WHATSAPP] No se pudo marcar mensaje como leído {}: {}", messageId, ex.getMessage());
            return false;
        }
    }

    /**
     * Envía un documento (PDF, etc.) vía WhatsApp.
     */
    public boolean sendDocumentMessage(String toPhoneNumber, String documentUrl, String filename, String caption) {
        if (whatsappBlockedByPlan()) {
            return false;
        }
        if (config.getToken() == null || config.getToken().isBlank() || config.getPhoneId() == null || config.getPhoneId().isBlank()) {
            return false;
        }

        try {
            String url = String.format("%s/%s/messages", WHATSAPP_API_URL, config.getPhoneId());
            String normalizedPhone = normalizePhoneNumber(toPhoneNumber);

            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", normalizedPhone);
            payload.put("type", "document");

            Map<String, String> doc = new HashMap<>();
            doc.put("link", documentUrl);
            if (filename != null && !filename.isBlank()) {
                doc.put("filename", filename);
            }
            if (caption != null && !caption.isBlank()) {
                doc.put("caption", caption);
            }
            payload.put("document", doc);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getToken());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            log.error("[WHATSAPP-DOCUMENT] ❌ Error enviando documento a {}: {}", toPhoneNumber, ex.getMessage());
            return false;
        }
    }

    /**
     * Envía una imagen vía WhatsApp.
     */
    public boolean sendImageMessage(String toPhoneNumber, String imageUrl, String caption) {
        if (whatsappBlockedByPlan()) {
            return false;
        }
        if (config.getToken() == null || config.getToken().isBlank() || config.getPhoneId() == null || config.getPhoneId().isBlank()) {
            return false;
        }

        try {
            String url = String.format("%s/%s/messages", WHATSAPP_API_URL, config.getPhoneId());
            String normalizedPhone = normalizePhoneNumber(toPhoneNumber);

            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", normalizedPhone);
            payload.put("type", "image");

            Map<String, String> image = new HashMap<>();
            image.put("link", imageUrl);
            if (caption != null && !caption.isBlank()) {
                image.put("caption", caption);
            }
            payload.put("image", image);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getToken());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            log.error("[WHATSAPP-IMAGE] ❌ Error enviando imagen a {}: {}", toPhoneNumber, ex.getMessage());
            return false;
        }
    }

    /**
     * Envía un mensaje interactivo con botones de respuesta rápida (hasta 3 botones).
     */
    public boolean sendInteractiveButtonMessage(String toPhoneNumber, String bodyText, List<Map<String, String>> buttons) {
        if (whatsappBlockedByPlan()) {
            return false;
        }
        if (config.getToken() == null || config.getToken().isBlank() || config.getPhoneId() == null || config.getPhoneId().isBlank()) {
            return false;
        }

        try {
            String url = String.format("%s/%s/messages", WHATSAPP_API_URL, config.getPhoneId());
            String normalizedPhone = normalizePhoneNumber(toPhoneNumber);

            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", normalizedPhone);
            payload.put("type", "interactive");

            Map<String, Object> interactive = new HashMap<>();
            interactive.put("type", "button");

            Map<String, String> body = new HashMap<>();
            body.put("text", bodyText);
            interactive.put("body", body);

            List<Map<String, Object>> actionButtons = new ArrayList<>();
            for (Map<String, String> btn : buttons) {
                Map<String, Object> b = new HashMap<>();
                b.put("type", "reply");
                Map<String, String> reply = new HashMap<>();
                reply.put("id", btn.get("id"));
                reply.put("title", btn.get("title"));
                b.put("reply", reply);
                actionButtons.add(b);
            }

            Map<String, Object> action = new HashMap<>();
            action.put("buttons", actionButtons);
            interactive.put("action", action);

            payload.put("interactive", interactive);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getToken());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            log.error("[WHATSAPP-INTERACTIVE] ❌ Error enviando botones interactivos a {}: {}", toPhoneNumber, ex.getMessage());
            return false;
        }
    }

    /**
     * Envía un mensaje interactivo con lista desplegable de opciones (hasta 10 filas).
     */
    public boolean sendInteractiveListMessage(String toPhoneNumber, String headerText, String bodyText, String footerText, String buttonLabel, List<Map<String, Object>> sections) {
        if (whatsappBlockedByPlan()) {
            return false;
        }
        if (config.getToken() == null || config.getToken().isBlank() || config.getPhoneId() == null || config.getPhoneId().isBlank()) {
            return false;
        }

        try {
            String url = String.format("%s/%s/messages", WHATSAPP_API_URL, config.getPhoneId());
            String normalizedPhone = normalizePhoneNumber(toPhoneNumber);

            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", normalizedPhone);
            payload.put("type", "interactive");

            Map<String, Object> interactive = new HashMap<>();
            interactive.put("type", "list");

            if (headerText != null && !headerText.isBlank()) {
                Map<String, String> header = new HashMap<>();
                header.put("type", "text");
                header.put("text", headerText);
                interactive.put("header", header);
            }

            Map<String, String> body = new HashMap<>();
            body.put("text", bodyText);
            interactive.put("body", body);

            if (footerText != null && !footerText.isBlank()) {
                Map<String, String> footer = new HashMap<>();
                footer.put("text", footerText);
                interactive.put("footer", footer);
            }

            Map<String, Object> action = new HashMap<>();
            action.put("button", (buttonLabel != null && !buttonLabel.isBlank()) ? buttonLabel : "Ver Opciones");
            action.put("sections", sections);
            interactive.put("action", action);

            payload.put("interactive", interactive);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getToken());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            log.error("[WHATSAPP-LIST] ❌ Error enviando lista interactiva a {}: {}", toPhoneNumber, ex.getMessage());
            return false;
        }
    }

    /**
     * Envía un mensaje de texto simple a un número de WhatsApp
     */
    @Async
    public void sendTextMessage(String toPhoneNumber, String message) {
        sendTextMessageSync(toPhoneNumber, message);
    }

    public boolean sendTextMessageSync(String toPhoneNumber, String message) {
        return sendTextMessageSync(toPhoneNumber, message, null);
    }

    public WhatsAppSendResult sendTextMessageDirect(String toPhoneNumber, String message) {
        if (whatsappBlockedByPlan()) {
            return WhatsAppSendResult.fail(PLAN_BLOCKED_ERROR);
        }
        try {
            if (config.getToken() == null || config.getToken().isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Token no configurado");
                return WhatsAppSendResult.fail("Token de WhatsApp no configurado");
            }

            if (config.getPhoneId() == null || config.getPhoneId().isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Phone ID no configurado");
                return WhatsAppSendResult.fail("Phone ID de WhatsApp no configurado");
            }

            String url = String.format("%s/%s/messages", WHATSAPP_API_URL, config.getPhoneId());
            String normalizedPhone = normalizePhoneNumber(toPhoneNumber);

            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", normalizedPhone);
            payload.put("type", "text");
            
            Map<String, String> text = new HashMap<>();
            text.put("body", message);
            payload.put("text", text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getToken());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            log.debug("[WHATSAPP-DEBUG] Enviando a URL: {}", url);
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            log.info("[WHATSAPP-RESPONSE] Status: {}", response.getStatusCode());
            log.info("[WHATSAPP-RESPONSE] Body: {}", response.getBody());

            String wamid = null;
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                try {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode messages = root.path("messages");
                    if (messages.isArray() && !messages.isEmpty()) {
                        wamid = messages.get(0).path("id").asText();
                    }
                } catch (Exception e) {
                    log.warn("[WHATSAPP-LOG] No se pudo parsear wamid de la respuesta: {}", e.getMessage());
                }
                return WhatsAppSendResult.ok(wamid);
            } else {
                return WhatsAppSendResult.fail("Status: " + response.getStatusCode() + " - " + response.getBody());
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String err = e.getStatusCode() + ": " + e.getResponseBodyAsString();
            log.error("[WHATSAPP] ❌ Error HTTP al enviar mensaje a {}: {}", toPhoneNumber, err);
            return WhatsAppSendResult.fail(err);
        } catch (Exception e) {
            log.error("[WHATSAPP] ❌ Excepción al enviar mensaje a {}: {}", toPhoneNumber, e.getMessage());
            return WhatsAppSendResult.fail(e.getMessage());
        }
    }

    public boolean sendTextMessageSync(String toPhoneNumber, String message, Appointment appointment) {
        String normalizedPhone = normalizePhoneNumber(toPhoneNumber);
        WhatsAppSendResult result = sendTextMessageDirect(toPhoneNumber, message);

        if (result.success()) {
            log.info("[WHATSAPP] ✅ Mensaje enviado exitosamente a {}. WAMID: {}", normalizedPhone, result.wamid());
            notificationLogService.logWhatsApp(appointment, normalizedPhone, "TEXT_MESSAGE", message, result.wamid());
            return true;
        } else {
            log.error("[WHATSAPP] ❌ Error al enviar mensaje a {}: {}", normalizedPhone, result.errorMessage());
            if (eventPublisher != null && !PLAN_BLOCKED_ERROR.equals(result.errorMessage())) {
                eventPublisher.publishEvent(WhatsAppFailedMessageEvent.builder()
                        .appointment(appointment)
                        .customer(appointment != null ? appointment.getCustomer() : null)
                        .recipientPhone(normalizedPhone)
                        .messageType(OutboxMessageType.TEXT)
                        .textContent(message)
                        .errorMessage(result.errorMessage())
                        .build());
            }
            return false;
        }
    }

    /**
     * Envía un template pre-aprobado de WhatsApp
     * Los templates deben estar aprobados en Meta Business Manager
     * 
     * @param toPhoneNumber Número de destino
     * @param templateName Nombre del template (ej: "hello_world")
     * @param languageCode Código de idioma (ej: "en_US", "es_MX")
     */
    @Async
    public void sendTemplate(String toPhoneNumber, String templateName, String languageCode) {
        sendTemplateSync(toPhoneNumber, templateName, languageCode, null, List.of());
    }

    /**
     * Envía un template pre-aprobado de WhatsApp con parámetros body.
     */
    @Async
    public void sendTemplate(String toPhoneNumber, String templateName, String languageCode, List<String> bodyParams) {
        sendTemplateSync(toPhoneNumber, templateName, languageCode, null, bodyParams);
    }

    /**
     * Envía un template pre-aprobado de WhatsApp con parámetros para HEADER y BODY.
     *
     * @param headerParam parámetro único para HEADER (si el template tiene HEADER con {{1}})
     * @param bodyParams parámetros del BODY en orden posicional
     */
    @Async
    public void sendTemplate(String toPhoneNumber,
                             String templateName,
                             String languageCode,
                             String headerParam,
                             List<String> bodyParams) {
        sendTemplateSync(toPhoneNumber, templateName, languageCode, headerParam, bodyParams);
    }

    public boolean sendTemplateSync(String toPhoneNumber,
                                    String templateName,
                                    String languageCode,
                                    String headerParam,
                                    List<String> bodyParams) {
        return sendTemplateSync(toPhoneNumber, templateName, languageCode, headerParam, bodyParams, null);
    }

    public WhatsAppSendResult sendTemplateDirect(String toPhoneNumber,
                                                 String templateName,
                                                 String languageCode,
                                                 String headerParam,
                                                 List<String> bodyParams) {
        if (whatsappBlockedByPlan()) {
            return WhatsAppSendResult.fail(PLAN_BLOCKED_ERROR);
        }
        try {
            if (config.getToken() == null || config.getToken().isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Token no configurado");
                return WhatsAppSendResult.fail("Token de WhatsApp no configurado");
            }

            if (config.getPhoneId() == null || config.getPhoneId().isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Phone ID no configurado");
                return WhatsAppSendResult.fail("Phone ID de WhatsApp no configurado");
            }

            String url = String.format("%s/%s/messages", WHATSAPP_API_URL, config.getPhoneId());
            String normalizedPhone = normalizePhoneNumber(toPhoneNumber);
            String lang = languageCode != null && !languageCode.isBlank() ? languageCode : config.getCitaConfirmadaLanguageCode();

            Map<String, Object> payload = messageBuilder.buildTemplatePayload(
                    normalizedPhone,
                    templateName,
                    lang,
                    headerParam,
                    bodyParams);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getToken());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            log.debug("[WHATSAPP-TEMPLATE] Enviando template '{}' a URL: {}", templateName, url);
            log.debug("[WHATSAPP-TEMPLATE] Payload: {}", payload);
            log.debug("[WHATSAPP-TEMPLATE] Número normalizado: {}", normalizedPhone);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            log.info("[WHATSAPP-TEMPLATE] Status: {}", response.getStatusCode());
            log.info("[WHATSAPP-TEMPLATE] Body: {}", response.getBody());

            String wamid = null;
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                try {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode messages = root.path("messages");
                    if (messages.isArray() && !messages.isEmpty()) {
                        wamid = messages.get(0).path("id").asText();
                    }
                } catch (Exception e) {
                    log.warn("[WHATSAPP-LOG] No se pudo parsear wamid de la respuesta: {}", e.getMessage());
                }
                return WhatsAppSendResult.ok(wamid);
            } else {
                return WhatsAppSendResult.fail("Status: " + response.getStatusCode() + " - " + response.getBody());
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String err = e.getStatusCode() + ": " + e.getResponseBodyAsString();
            log.error("[WHATSAPP] ❌ Error HTTP al enviar template '{}' a {}: {}", templateName, toPhoneNumber, err);
            return WhatsAppSendResult.fail(err);
        } catch (Exception e) {
            log.error("[WHATSAPP] ❌ Excepción al enviar template '{}' a {}: {}", templateName, toPhoneNumber, e.getMessage());
            return WhatsAppSendResult.fail(e.getMessage());
        }
    }

    public boolean sendTemplateSync(String toPhoneNumber,
                                    String templateName,
                                    String languageCode,
                                    String headerParam,
                                    List<String> bodyParams,
                                    Appointment appointment) {
        String normalizedPhone = normalizePhoneNumber(toPhoneNumber);
        WhatsAppSendResult result = sendTemplateDirect(toPhoneNumber, templateName, languageCode, headerParam, bodyParams);

        if (result.success()) {
            log.info("[WHATSAPP] ✅ Template '{}' enviado exitosamente a {}. WAMID: {}", templateName, normalizedPhone, result.wamid());
            String contentSummary = String.format("Template: %s | Header: %s | Params: %s", 
                    templateName, headerParam, bodyParams);
            notificationLogService.logWhatsApp(appointment, normalizedPhone, templateName, contentSummary, result.wamid());
            return true;
        } else {
            log.error("[WHATSAPP] ❌ Error al enviar template '{}' a {}: {}", templateName, normalizedPhone, result.errorMessage());
            if (eventPublisher != null && !PLAN_BLOCKED_ERROR.equals(result.errorMessage())) {
                eventPublisher.publishEvent(WhatsAppFailedMessageEvent.builder()
                        .appointment(appointment)
                        .customer(appointment != null ? appointment.getCustomer() : null)
                        .recipientPhone(normalizedPhone)
                        .messageType(OutboxMessageType.TEMPLATE)
                        .templateName(templateName)
                        .languageCode(languageCode)
                        .headerParam(headerParam)
                        .bodyParams(bodyParams)
                        .errorMessage(result.errorMessage())
                        .build());
            }
            return false;
        }
    }

    /**
     * Normaliza el número de teléfono eliminando caracteres especiales
     * y asegurando que tenga el formato correcto para WhatsApp API
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null) {
            return "";
        }
        // Eliminar espacios, guiones, paréntesis y el símbolo +
        String normalized = phone.replaceAll("[\\s\\-()]+", "");
        
        // Si el número no empieza con código de país, asumimos Chile (56)
        if (!normalized.startsWith("56") && !normalized.startsWith("+")) {
            normalized = "56" + normalized;
        }
        
        // Eliminar el + si existe
        normalized = normalized.replace("+", "");
        
        return normalized;
    }


}
