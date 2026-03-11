package cl.bunnycure.service;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.web.dto.WhatsAppWebhookDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio para procesar las notificaciones de webhook de WhatsApp
 */
@Service
public class WhatsAppWebhookService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookService.class);
    private static final long DEDUPE_TTL_MILLIS = 10 * 60 * 1000L;
    private static final Pattern APPOINTMENT_ID_PATTERN = Pattern.compile("(?:^|[:#_\\-])(\\d+)$");

    private final Map<String, Long> processedEventIds = new ConcurrentHashMap<>();
    private final AppointmentRepository appointmentRepository;
    private final WhatsAppService whatsAppService;

    public WhatsAppWebhookService(AppointmentRepository appointmentRepository, WhatsAppService whatsAppService) {
        this.appointmentRepository = appointmentRepository;
        this.whatsAppService = whatsAppService;
    }

    public boolean isSignatureValid(String rawPayload, String signatureHeader, String appSecret) {
        if (appSecret == null || appSecret.isBlank()) {
            // Signature verification can be disabled explicitly in non-production environments.
            return true;
        }

        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            log.warn("[WEBHOOK] ⚠️ Missing or invalid X-Hub-Signature-256 header");
            return false;
        }

        try {
            String expected = signatureHeader.substring("sha256=".length()).trim().toLowerCase();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
            String actual = toHex(digest);
            return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("[WEBHOOK] ❌ Error validating webhook signature", e);
            return false;
        }
    }

    /**
     * Procesa la notificación recibida del webhook
     */
    public void processWebhookNotification(WhatsAppWebhookDto webhook) {
        try {
            log.info("[WEBHOOK] 📥 Notificación recibida de WhatsApp");
            log.info("[WEBHOOK] Object type: {}", webhook.getObject());

            if (webhook.getEntry() == null || webhook.getEntry().isEmpty()) {
                log.warn("[WEBHOOK] ⚠️ No se encontraron entries en la notificación");
                return;
            }

            // Procesar cada entry
            for (WhatsAppWebhookDto.Entry entry : webhook.getEntry()) {
                processEntry(entry);
            }

        } catch (Exception e) {
            log.error("[WEBHOOK] ❌ Error procesando notificación: {}", e.getMessage(), e);
        }
    }

    private void processEntry(WhatsAppWebhookDto.Entry entry) {
        log.info("[WEBHOOK] 📦 Procesando entry ID: {}", entry.getId());

        if (entry.getChanges() == null || entry.getChanges().isEmpty()) {
            log.warn("[WEBHOOK] ⚠️ No se encontraron cambios en el entry");
            return;
        }

        for (WhatsAppWebhookDto.Change change : entry.getChanges()) {
            processChange(change);
        }
    }

    private void processChange(WhatsAppWebhookDto.Change change) {
        String field = change.getField();
        log.info("[WEBHOOK] 🔄 Procesando cambio en campo: {}", field);

        WhatsAppWebhookDto.Value value = change.getValue();
        if (value == null) {
            log.warn("[WEBHOOK] ⚠️ No se encontró value en el cambio");
            return;
        }

        log.info("[WEBHOOK] 📱 Messaging product: {}", value.getMessagingProduct());
        
        if (value.getMetadata() != null) {
            log.info("[WEBHOOK] 📞 Phone Number ID: {}", value.getMetadata().getPhoneNumberId());
            log.info("[WEBHOOK] 📞 Display Phone: {}", value.getMetadata().getDisplayPhoneNumber());
        }

        // Procesar según el tipo de campo de webhook
        switch (field) {
            case "messages":
                // Procesar mensajes recibidos
                if (value.getMessages() != null && !value.getMessages().isEmpty()) {
                    processIncomingMessages(value.getMessages(), value.getContacts());
                }
                // Procesar estados de mensajes (entregado, leído, etc.)
                if (value.getStatuses() != null && !value.getStatuses().isEmpty()) {
                    processMessageStatuses(value.getStatuses());
                }
                break;
                
            case "message_template_status_update":
                log.info("[WEBHOOK] 📋 Actualización de estado de plantilla de mensaje");
                log.debug("[WEBHOOK] Valor: {}", value);
                // TODO: Implementar lógica para tracking de estado de templates
                break;
                
            case "message_template_quality_update":
                log.info("[WEBHOOK] ⭐ Actualización de calidad de plantilla");
                log.debug("[WEBHOOK] Valor: {}", value);
                // TODO: Implementar lógica para monitoreo de calidad de templates
                break;
                
            case "phone_number_name_update":
                log.info("[WEBHOOK] 📱 Actualización de nombre del número de teléfono");
                log.debug("[WEBHOOK] Valor: {}", value);
                break;
                
            case "phone_number_quality_update":
                log.info("[WEBHOOK] 📊 Actualización de calidad del número de teléfono");
                log.debug("[WEBHOOK] Valor: {}", value);
                // TODO: Implementar alertas si la calidad del número baja
                break;
                
            case "account_alerts":
                log.warn("[WEBHOOK] ⚠️ Alerta de cuenta");
                log.warn("[WEBHOOK] Valor: {}", value);
                // TODO: Implementar notificaciones para alertas de cuenta
                break;
                
            case "account_update":
                log.info("[WEBHOOK] 🔄 Actualización de cuenta");
                log.debug("[WEBHOOK] Valor: {}", value);
                break;
                
            case "business_capability_update":
                log.info("[WEBHOOK] 💼 Actualización de capacidades del negocio");
                log.debug("[WEBHOOK] Valor: {}", value);
                break;
                
            default:
                log.info("[WEBHOOK] ℹ️ Evento de webhook recibido: {}", field);
                log.debug("[WEBHOOK] Valor: {}", value);
                // Otros eventos no manejados específicamente aún
        }
    }

    private void processIncomingMessages(
            java.util.List<WhatsAppWebhookDto.Message> messages,
            java.util.List<WhatsAppWebhookDto.Contact> contacts) {

        log.info("[WEBHOOK] 💬 Procesando {} mensaje(s) entrante(s)", messages.size());

        for (WhatsAppWebhookDto.Message message : messages) {
            if (isDuplicateEvent("msg:" + message.getId())) {
                log.info("[WEBHOOK] ♻️ Message already processed, skipping id={}", message.getId());
                continue;
            }

            String contactName = "Unknown";
            if (contacts != null && !contacts.isEmpty()) {
                WhatsAppWebhookDto.Contact contact = contacts.get(0);
                if (contact.getProfile() != null && contact.getProfile().getName() != null) {
                    contactName = contact.getProfile().getName();
                }
            }

            log.info("[WEBHOOK] 📨 Mensaje de: {} ({})", contactName, message.getFrom());
            log.info("[WEBHOOK] 🆔 Message ID: {}", message.getId());
            log.info("[WEBHOOK] ⏰ Timestamp: {}", message.getTimestamp());
            log.info("[WEBHOOK] 📝 Tipo: {}", message.getType());

            // Procesar según el tipo de mensaje
            switch (message.getType()) {
                case "text":
                    processTextMessage(message);
                    break;

                case "image":
                    if (message.getImage() != null) {
                        log.info("[WEBHOOK] 🖼️ Imagen recibida - ID: {}", message.getImage().getId());
                        log.info("[WEBHOOK] 📝 Caption: {}", message.getImage().getCaption());
                    }
                    break;
                
                case "video":
                    if (message.getVideo() != null) {
                        log.info("[WEBHOOK] 🎥 Video recibido - ID: {}", message.getVideo().getId());
                    }
                    break;
                
                case "audio":
                    if (message.getAudio() != null) {
                        log.info("[WEBHOOK] 🎵 Audio recibido - ID: {}", message.getAudio().getId());
                    }
                    break;
                
                case "document":
                    if (message.getDocument() != null) {
                        log.info("[WEBHOOK] 📄 Documento recibido - ID: {}", message.getDocument().getId());
                        log.info("[WEBHOOK] 📝 Filename: {}", message.getDocument().getFilename());
                    }
                    break;

                case "button":
                    processButtonMessage(message);
                    break;

                case "interactive":
                    processInteractiveMessage(message);
                    break;
                
                default:
                    log.info("[WEBHOOK] ❓ Tipo de mensaje no manejado: {}", message.getType());
            }
        }
    }

    private void processTextMessage(WhatsAppWebhookDto.Message message) {
        if (message.getText() == null || message.getText().getBody() == null) {
            return;
        }

        String text = message.getText().getBody().trim();
        log.info("[WEBHOOK] 💬 Texto: {}", text);
        if (text.isEmpty() || message.getFrom() == null || message.getFrom().isBlank()) {
            return;
        }

        whatsAppService.sendTextMessage(
                message.getFrom(),
                "Hola! Gracias por escribir a BunnyCure. " +
                        "Si quieres confirmar una cita, responde con el boton de confirmacion del mensaje que te enviamos."
        );
    }

    private void processButtonMessage(WhatsAppWebhookDto.Message message) {
        if (message.getButton() == null) {
            log.warn("[WEBHOOK] ⚠️ Mensaje tipo button sin contenido button");
            return;
        }

        String text = message.getButton().getText();
        String payload = message.getButton().getPayload();
        log.info("[WEBHOOK] 🔘 Button text: {}", text);
        log.info("[WEBHOOK] 🔘 Button payload: {}", payload);

        if (isConfirmPayload(text, payload)) {
            handleConfirmAttendance(message);
            return;
        }

        log.info("[WEBHOOK] ℹ️ Payload de button no mapeado: {}", payload);
    }

    private boolean isConfirmPayload(String text, String payload) {
        String normalizedPayload = normalizeKey(payload);
        String normalizedText = normalizeKey(text);
        return normalizedPayload.contains("confirmar")
                || normalizedPayload.contains("confirmacion")
                || normalizedPayload.contains("confirmar_asistencia")
                || normalizedText.contains("confirmar");
    }

    private void handleConfirmAttendance(WhatsAppWebhookDto.Message message) {
        String from = message.getFrom();
        if (from == null || from.isBlank()) {
            log.warn("[WEBHOOK] ⚠️ No se puede confirmar asistencia: campo from vacío");
            return;
        }

        Optional<Appointment> appointment = findAppointmentToConfirm(message);
        if (appointment.isEmpty()) {
            log.warn("[WEBHOOK] ⚠️ No se encontró cita pendiente para confirmar. from={}", from);
            whatsAppService.sendTextMessage(from, "No encontré una cita pendiente asociada a este numero.");
            return;
        }

        Appointment target = appointment.get();
        if (target.getStatus() == AppointmentStatus.CONFIRMED) {
            whatsAppService.sendTextMessage(from, "Tu cita ya estaba confirmada. Te esperamos en BunnyCure.");
            return;
        }

        target.setStatus(AppointmentStatus.CONFIRMED);
        appointmentRepository.save(target);
        log.info("[WEBHOOK] ✅ Cita confirmada desde button. appointmentId={}", target.getId());
        whatsAppService.sendTextMessage(from, "Perfecto! Tu cita quedó confirmada. Te esperamos en BunnyCure.");
    }

    private Optional<Appointment> findAppointmentToConfirm(WhatsAppWebhookDto.Message message) {
        Optional<Long> payloadAppointmentId = extractAppointmentId(message);
        if (payloadAppointmentId.isPresent()) {
            return appointmentRepository.findByIdWithDetails(payloadAppointmentId.get())
                    .filter(a -> a.getStatus() == AppointmentStatus.PENDING || a.getStatus() == AppointmentStatus.CONFIRMED);
        }

        String normalizedFrom = normalizePhone(message.getFrom());
        LocalDate today = LocalDate.now();

        return appointmentRepository.findByStatus(AppointmentStatus.PENDING).stream()
                .filter(a -> a.getAppointmentDate() != null && !a.getAppointmentDate().isBefore(today))
                .filter(a -> a.getCustomer() != null)
                .filter(a -> normalizePhone(a.getCustomer().getPhone()).equals(normalizedFrom))
                .findFirst();
    }

    private Optional<Long> extractAppointmentId(WhatsAppWebhookDto.Message message) {
        if (message.getButton() == null || message.getButton().getPayload() == null) {
            return Optional.empty();
        }

        String payload = message.getButton().getPayload().trim();
        Matcher matcher = APPOINTMENT_ID_PATTERN.matcher(payload);
        if (!matcher.find()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException ex) {
            log.warn("[WEBHOOK] ⚠️ No se pudo parsear appointment id desde payload={}", payload);
            return Optional.empty();
        }
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }
        return phone.replaceAll("\\D", "");
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void processMessageStatuses(java.util.List<WhatsAppWebhookDto.Status> statuses) {
        log.info("[WEBHOOK] 📊 Procesando {} estado(s) de mensaje(s)", statuses.size());

        for (WhatsAppWebhookDto.Status status : statuses) {
            if (isDuplicateEvent("status:" + status.getId())) {
                log.info("[WEBHOOK] ♻️ Status already processed, skipping id={}", status.getId());
                continue;
            }

            log.info("[WEBHOOK] 📬 Estado de mensaje");
            log.info("[WEBHOOK] 🆔 Message ID: {}", status.getId());
            log.info("[WEBHOOK] 📱 Recipient ID: {}", status.getRecipientId());
            log.info("[WEBHOOK] ✅ Estado: {}", status.getStatus());
            log.info("[WEBHOOK] ⏰ Timestamp: {}", status.getTimestamp());

            // Estados posibles: sent, delivered, read, failed
            switch (status.getStatus()) {
                case "sent":
                    log.info("[WEBHOOK] 📤 Mensaje enviado exitosamente");
                    break;
                case "delivered":
                    log.info("[WEBHOOK] 📥 Mensaje entregado al destinatario");
                    break;
                case "read":
                    log.info("[WEBHOOK] 👀 Mensaje leído por el destinatario");
                    break;
                case "failed":
                    log.error("[WEBHOOK] ❌ Mensaje falló al enviarse");
                    break;
                default:
                    log.info("[WEBHOOK] ℹ️ Estado: {}", status.getStatus());
            }

            // Información adicional
            if (status.getConversation() != null) {
                log.info("[WEBHOOK] 💬 Conversation ID: {}", status.getConversation().getId());
                if (status.getConversation().getOrigin() != null) {
                    log.info("[WEBHOOK] 🔄 Origin type: {}", status.getConversation().getOrigin().getType());
                }
            }

            if (status.getPricing() != null) {
                log.info("[WEBHOOK] 💰 Billable: {}", status.getPricing().isBillable());
                log.info("[WEBHOOK] 💰 Category: {}", status.getPricing().getCategory());
            }
        }
    }

    private boolean isDuplicateEvent(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long previous = processedEventIds.putIfAbsent(eventId, now);
        cleanupOldEvents(now);
        return previous != null && (now - previous) < DEDUPE_TTL_MILLIS;
    }

    private void cleanupOldEvents(long now) {
        if (processedEventIds.size() < 5000) {
            return;
        }
        processedEventIds.entrySet().removeIf(entry -> (now - entry.getValue()) > DEDUPE_TTL_MILLIS);
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void processInteractiveMessage(WhatsAppWebhookDto.Message message) {
        if (message.getInteractive() == null) {
            log.warn("[WEBHOOK] ⚠️ Mensaje tipo interactive sin contenido interactive");
            return;
        }

        log.info("[WEBHOOK] 🧩 Interactive type: {}", message.getInteractive().getType());

        if (message.getInteractive().getButtonReply() != null) {
            var reply = message.getInteractive().getButtonReply();
            log.info("[WEBHOOK] 🔘 Button reply id: {}", reply.getId());
            log.info("[WEBHOOK] 🔘 Button reply title: {}", reply.getTitle());
        }

        if (message.getInteractive().getListReply() != null) {
            var reply = message.getInteractive().getListReply();
            log.info("[WEBHOOK] 📋 List reply id: {}", reply.getId());
            log.info("[WEBHOOK] 📋 List reply title: {}", reply.getTitle());
            log.info("[WEBHOOK] 📋 List reply description: {}", reply.getDescription());
        }
    }
}