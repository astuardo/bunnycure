package cl.bunnycure.service;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.WebhookOperationalEvent;
import cl.bunnycure.domain.model.WebhookProcessedEvent;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.WebhookOperationalEventRepository;
import cl.bunnycure.domain.repository.WebhookProcessedEventRepository;
import cl.bunnycure.web.dto.WhatsAppWebhookDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio para procesar las notificaciones de webhook de WhatsApp
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppWebhookService {
    private static final long DEDUPE_TTL_MILLIS = 10 * 60 * 1000L;
    private static final long DEDUPE_CLEANUP_EVERY_EVENTS = 250;
    private static final long ALERT_THROTTLE_MILLIS = 5 * 60 * 1000L;
    private static final Pattern APPOINTMENT_ID_PATTERN = Pattern.compile("(?:^|[:#_\\-])(\\d+)$");

    private final Map<String, Long> processedEventIds = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAlertByKey = new ConcurrentHashMap<>();
    private final Map<String, Long> operationalEventCounters = new ConcurrentHashMap<>();
    private final AtomicLong dedupeChecksCounter = new AtomicLong(0);
    private final AppointmentRepository appointmentRepository;
    private final WebhookOperationalEventRepository webhookOperationalEventRepository;
    private final WebhookProcessedEventRepository webhookProcessedEventRepository;
    private final WhatsAppService whatsAppService;
    private final AppSettingsService appSettingsService;
    private final WhatsAppHandoffService whatsAppHandoffService;
    private final CustomerServiceRecordService customerServiceRecordService;
    private final WebPushNotificationService webPushNotificationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private cl.bunnycure.service.marketing.MarketingTemplateAiService marketingTemplateAiService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private cl.bunnycure.domain.repository.MarketingTemplateRepository marketingTemplateRepository;

    @Value("${bunnycure.whatsapp.number:}")
    private String adminWhatsAppNumber;

    @Value("${whatsapp.webhook.alert-admin:false}")
    private boolean alertAdminOnRiskEvents;

    @Value("${whatsapp.webhook.customer-record.authorized-numbers:}")
    private String customerRecordAuthorizedNumbers;

    @Value("${app.frontend.base-url:https://bunnycure-frontend.vercel.app}")
    private String frontendBaseUrl;

    public void setMarketingTemplateAiService(cl.bunnycure.service.marketing.MarketingTemplateAiService marketingTemplateAiService) {
        this.marketingTemplateAiService = marketingTemplateAiService;
    }

    public void setMarketingTemplateRepository(cl.bunnycure.domain.repository.MarketingTemplateRepository marketingTemplateRepository) {
        this.marketingTemplateRepository = marketingTemplateRepository;
    }

    public boolean isSignatureValid(String rawPayload, String signatureHeader, String appSecret) {
        byte[] payloadBytes = rawPayload != null
                ? rawPayload.getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        return isSignatureValid(payloadBytes, signatureHeader, appSecret);
    }

    public boolean isSignatureValid(byte[] rawPayloadBytes, String signatureHeader, String appSecret) {
        String cleanSecret = cleanAppSecret(appSecret);
        if (cleanSecret.isBlank()) {
            // Signature verification can be disabled explicitly in non-production environments.
            return true;
        }

        if (cleanSecret.startsWith("EAAG") || cleanSecret.startsWith("EAA")) {
            log.error("[WEBHOOK] ❌ CRITICAL: whatsapp.webhook.app-secret appears to be an Access Token (starts with EAA...) instead of the Meta App Secret (App Dashboard -> Basic Settings -> App Secret). Signatures will fail!");
        }

        List<String> expectedSignatures = extractExpectedSignatures(signatureHeader);
        if (expectedSignatures.isEmpty()) {
            log.warn("[WEBHOOK] ⚠️ Missing or invalid X-Hub-Signature-256 header");
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cleanSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(rawPayloadBytes != null ? rawPayloadBytes : new byte[0]);
            String actual = toHex(digest);
            boolean valid = expectedSignatures.stream().anyMatch(expected ->
                    MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))
            );
            if (!valid) {
                log.warn("[WEBHOOK] ⚠️ Signature mismatch. expectedPrefix={}, actualPrefix={}, payloadBytes={}",
                        safePrefix(expectedSignatures.get(0)), safePrefix(actual), rawPayloadBytes != null ? rawPayloadBytes.length : 0);
            }
            return valid;
        } catch (Exception e) {
            log.error("[WEBHOOK] ❌ Error validating webhook signature", e);
            return false;
        }
    }

    private List<String> extractExpectedSignatures(String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return List.of();
        }

        List<String> signatures = new ArrayList<>();
        String[] candidates = signatureHeader.split(",");
        for (String rawCandidate : candidates) {
            if (rawCandidate == null) {
                continue;
            }
            String candidate = rawCandidate.trim();
            if (candidate.isEmpty()) {
                continue;
            }

            String normalized = candidate.toLowerCase(Locale.ROOT);
            if (!normalized.startsWith("sha256=")) {
                continue;
            }

            String value = normalized.substring("sha256=".length()).trim();
            if (value.length() != 64 || !value.matches("[0-9a-f]{64}")) {
                continue;
            }
            signatures.add(value);
        }
        return signatures;
    }

    private String cleanAppSecret(String secret) {
        if (secret == null) {
            return "";
        }
        String s = secret.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            if (s.length() >= 2) {
                s = s.substring(1, s.length() - 1).trim();
            }
        }
        return s;
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
                handleTemplateStatusUpdate(value);
                handleOperationalWebhookEvent("message_template_status_update", value, isTemplateStatusRisk(value));
                break;
                
            case "message_template_quality_update":
                handleOperationalWebhookEvent("message_template_quality_update", value, true);
                break;
                
            case "phone_number_name_update":
                log.info("[WEBHOOK] 📱 Actualización de nombre del número de teléfono");
                log.debug("[WEBHOOK] Valor: {}", value);
                break;
                
            case "phone_number_quality_update":
                handleOperationalWebhookEvent("phone_number_quality_update", value, true);
                break;
                
            case "account_alerts":
                handleOperationalWebhookEvent("account_alerts", value, true);
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

            // Marcar mensaje como leído en Meta (doble check azul para la clienta)
            if (message.getId() != null && !message.getId().isBlank()) {
                whatsAppService.markMessageAsRead(message.getId());
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
                        if (isCustomerRecordOwnerMessage(message)) {
                            customerServiceRecordService.registerFromIncomingImage(message)
                                    .ifPresent(record -> log.info(
                                            "[WEBHOOK] ✅ Customer service record saved. customerId={}, recordId={}",
                                            record.getCustomer().getId(),
                                            record.getId()
                                    ));
                        } else {
                            log.info("[WEBHOOK] ℹ️ Imagen ignorada para ficha cliente. sender={}", message.getFrom());
                        }
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

        // Si es la administradora/dueña y solicita crear una plantilla de marketing
        if (isCustomerRecordOwnerMessage(message) && isMarketingTemplateAiCommand(text)) {
            handleAdminAiTemplateCreation(message, text);
            return;
        }

        // Si el cliente responde confirmando la cita por texto (ej: "Confirmo", "Si", "Confirmar")
        if (isConfirmPayload(text, "")) {
            log.info("[WEBHOOK] 💬 Confirmación detectada en mensaje de texto para from={}", message.getFrom());
            handleConfirmAttendance(message);
            return;
        }

        // Si el cliente solicita reprogramar por texto (ej: "reprogramar", "cambiar hora")
        if (isReschedulePayload(text, "")) {
            log.info("[WEBHOOK] 💬 Reprogramación detectada en mensaje de texto para from={}", message.getFrom());
            handleRescheduleRequest(message);
            return;
        }

        if (isHandoffEnabled()) {
            sendHandoffMessage(message.getFrom(), "text_free_form");
            return;
        }

        whatsAppService.sendTextMessage(
                message.getFrom(),
                "Hola! Gracias por escribir a BunnyCure. " +
                        "Si quieres confirmar una cita, responde con el boton de confirmacion del mensaje que te enviamos."
        );
    }

    private boolean isMarketingTemplateAiCommand(String text) {
        String lower = text.toLowerCase(Locale.ROOT).trim();
        return lower.startsWith("crear plantilla")
                || lower.startsWith("nueva plantilla")
                || lower.startsWith("crea plantilla")
                || lower.startsWith("plantilla:")
                || lower.startsWith("promo:")
                || lower.startsWith("crea una promo")
                || lower.startsWith("crear promo")
                || lower.startsWith("genera plantilla")
                || lower.startsWith("generar plantilla");
    }

    private void handleAdminAiTemplateCreation(WhatsAppWebhookDto.Message message, String text) {
        if (marketingTemplateAiService == null) {
            log.warn("[WEBHOOK-AI] ⚠️ Solicitud de plantilla recibida pero marketingTemplateAiService no está disponible");
            whatsAppService.sendTextMessage(message.getFrom(), "⚠️ Servicio de IA para plantillas no disponible en este momento.");
            return;
        }

        try {
            log.info("[WEBHOOK-AI] 🤖 Solicitud de plantilla recibida desde WhatsApp admin (from={}): {}", message.getFrom(), text);

            String prompt = cleanAiCommandPrompt(text);
            whatsAppService.sendTextMessage(
                    message.getFrom(),
                    "🤖 *Agente BunnyCure IA:* Procesando tu instrucción para la plantilla de marketing:\n"
                            + "«" + prompt + "»\n\n"
                            + "Validando políticas de Meta y registrando en caliente, dame un momento por favor 💅✨"
            );

            cl.bunnycure.web.dto.marketing.MarketingTemplateDto result =
                    marketingTemplateAiService.generateAndSaveTemplate(prompt, true, "WHATSAPP_ADMIN");

            StringBuilder reply = new StringBuilder();
            reply.append("✨ *¡Plantilla Creada y Registrada con Éxito!*\n\n");
            reply.append("📌 *Nombre Meta:* `").append(result.getName()).append("`\n");
            reply.append("🏷️ *Campaña:* ").append(result.getDisplayName()).append("\n");
            if (result.getHeaderText() != null && !result.getHeaderText().isBlank()) {
                reply.append("📢 *Cabecera:* ").append(result.getHeaderText()).append("\n\n");
            }
            reply.append("💬 *Cuerpo:* \n\"").append(result.getBodyText()).append("\"\n\n");
            if (result.getButtonText() != null && !result.getButtonText().isBlank()) {
                reply.append("🔗 *Botón:* ").append(result.getButtonText()).append(" (").append(result.getButtonUrl()).append(")\n\n");
            }

            if (result.getMetaId() != null && !result.getMetaId().isBlank()) {
                reply.append("🚀 *Estado en Meta:* Enviada a revisión automática (ID: `").append(result.getMetaId()).append("`).\n");
                reply.append("Te avisaré automáticamente por acá en cuanto Meta la apruebe.");
            } else {
                reply.append("💾 Guardada en el catálogo interno de BunnyCure.");
            }

            reply.append("\n\n📱 Ver en panel web: ")
                    .append(frontendBaseUrl != null ? frontendBaseUrl : "https://bunnycure-frontend.vercel.app")
                    .append("/marketing");

            whatsAppService.sendTextMessage(message.getFrom(), reply.toString());
        } catch (Exception ex) {
            log.error("[WEBHOOK-AI] ❌ Error creando plantilla desde WhatsApp: {}", ex.getMessage(), ex);
            whatsAppService.sendTextMessage(message.getFrom(), "❌ *Agente BunnyCure IA:* Ocurrió un error al generar la plantilla: " + ex.getMessage());
        }
    }

    private String cleanAiCommandPrompt(String text) {
        return text.replaceFirst("(?i)^(crear plantilla|nueva plantilla|crea plantilla|plantilla:|promo:|crea una promo|crear promo|genera plantilla|generar plantilla)\\s*[:,-]?\\s*", "").trim();
    }

    private void handleTemplateStatusUpdate(WhatsAppWebhookDto.Value value) {
        if (marketingTemplateRepository == null || value == null || value.getExtraFields() == null) {
            return;
        }

        Map<String, Object> extra = value.getExtraFields();
        String templateName = null;
        if (extra.containsKey("message_template_name")) {
            templateName = String.valueOf(extra.get("message_template_name"));
        } else if (extra.containsKey("template_name")) {
            templateName = String.valueOf(extra.get("template_name"));
        } else if (extra.containsKey("element_name")) {
            templateName = String.valueOf(extra.get("element_name"));
        }

        String event = extra.containsKey("event") ? String.valueOf(extra.get("event")) : null;
        if (event == null && extra.containsKey("status")) {
            event = String.valueOf(extra.get("status"));
        }

        if (templateName != null && event != null) {
            log.info("[WEBHOOK-TEMPLATE] 🔄 Meta notificó actualización de plantilla: name='{}', event='{}'", templateName, event);
            Optional<cl.bunnycure.domain.model.MarketingTemplateEntity> opt = marketingTemplateRepository.findByNameIgnoreCase(templateName);
            if (opt.isPresent()) {
                cl.bunnycure.domain.model.MarketingTemplateEntity entity = opt.get();
                entity.setMetaStatus(event.toUpperCase(Locale.ROOT));
                marketingTemplateRepository.save(entity);
                log.info("[WEBHOOK-TEMPLATE] ✅ Estado de plantilla '{}' actualizado a '{}' en BD", entity.getName(), entity.getMetaStatus());

                if ("APPROVED".equalsIgnoreCase(event)) {
                    String adminPhone = resolveAdminWhatsAppNumber();
                    if (adminPhone != null && !adminPhone.isBlank()) {
                        String msg = "🎉 *¡Meta aprobó tu plantilla de marketing!*\n\n"
                                + "La plantilla *\"" + entity.getDisplayName() + "\"* (`" + entity.getName() + "`) "
                                + "ha sido aprobada oficialmente por WhatsApp.\n\n"
                                + "Ya está disponible para despacho en tu panel: "
                                + (frontendBaseUrl != null ? frontendBaseUrl : "https://bunnycure-frontend.vercel.app") + "/marketing";
                        whatsAppService.sendTextMessage(adminPhone, msg);
                    }
                }
            }
        }
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

        if (isReschedulePayload(text, payload)) {
            handleRescheduleRequest(message);
            return;
        }

        log.info("[WEBHOOK] ℹ️ Payload de button no mapeado: {}", payload);
        if (message.getFrom() != null && !message.getFrom().isBlank() && isHandoffEnabled()) {
            sendHandoffMessage(message.getFrom(), "button_unmapped");
        }
    }

    private boolean isConfirmPayload(String text, String payload) {
        String normalizedPayload = normalizeKey(payload);
        String normalizedText = normalizeKey(text);

        if (normalizedPayload.contains("confirmar")
                || normalizedPayload.contains("confirmacion")
                || normalizedPayload.contains("confirmar_asistencia")
                || normalizedPayload.contains("confirm")) {
            return true;
        }

        if (normalizedText.contains("confirmar")
                || normalizedText.contains("confirmo")
                || normalizedText.contains("confirmada")
                || normalizedText.contains("confirmado")
                || normalizedText.contains("confirmacion")
                || normalizedText.contains("asistire")
                || normalizedText.contains("asistiré")) {
            return true;
        }

        // Respuestas afirmativas breves directas
        return normalizedText.equals("si")
                || normalizedText.equals("sí")
                || normalizedText.startsWith("si ")
                || normalizedText.startsWith("sí ")
                || normalizedText.equals("voy")
                || normalizedText.equals("ok")
                || normalizedText.equals("dale");
    }

    private boolean isReschedulePayload(String text, String payload) {
        String normalizedPayload = normalizeKey(payload);
        String normalizedText = normalizeKey(text);
        return normalizedPayload.contains("reprogramar")
                || normalizedPayload.contains("reagendar")
                || normalizedPayload.contains("cambiar_hora")
                || normalizedPayload.contains("cambiar_cita")
                || normalizedPayload.contains("cancelar")
                || normalizedText.contains("reprogramar")
                || normalizedText.contains("reagendar")
                || normalizedText.contains("cambiar hora")
                || normalizedText.contains("cambiar cita")
                || normalizedText.contains("cancelar cita")
                || normalizedText.contains("no podre")
                || normalizedText.contains("no podré")
                || normalizedText.contains("no voy");
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

    private void handleRescheduleRequest(WhatsAppWebhookDto.Message message) {
        String from = message.getFrom();
        if (from == null || from.isBlank()) {
            log.warn("[WEBHOOK] ⚠️ No se puede procesar reprogramación: campo from vacío");
            return;
        }

        Optional<Appointment> appointmentOpt = findAppointmentToReschedule(message);
        if (appointmentOpt.isEmpty()) {
            log.warn("[WEBHOOK] ⚠️ No se encontró cita pendiente o confirmada para reprogramar. from={}", from);
            return;
        }

        Appointment appointment = appointmentOpt.get();
        appointment.setStatus(AppointmentStatus.RESCHEDULE_REQUESTED);
        appointmentRepository.save(appointment);

        String customerName = appointment.getCustomer() != null ? appointment.getCustomer().getFullName() : "Cliente";
        String customerPhone = appointment.getCustomer() != null ? appointment.getCustomer().getPhone() : from;
        String serviceName = appointment.getService() != null ? appointment.getService().getName() : "Servicio";
        String fecha = appointment.getAppointmentDate() != null ? appointment.getAppointmentDate().toString() : "";
        String hora = appointment.getAppointmentTime() != null ? appointment.getAppointmentTime().toString() : "";

        log.info("[WEBHOOK] 🔄 Cita ID={} marcada como RESCHEDULE_REQUESTED para clienta {}", appointment.getId(), customerName);

        // 1. Notificación Web Push a la App (para que salte en el panel admin)
        try {
            String pushTitle = "🔄 Cita necesita ser reprogramada";
            String pushBody = String.format("%s solicitó reprogramar su cita del %s a las %s (%s).",
                    customerName, fecha, hora, serviceName);
            webPushNotificationService.sendAdminCustomNotification(pushTitle, pushBody, "/appointments?status=RESCHEDULE_REQUESTED");
            log.info("[WEBHOOK] 📲 Notificación Web Push de reprogramación enviada exitosamente para cita ID: {}", appointment.getId());
        } catch (Exception ex) {
            log.error("[WEBHOOK] ❌ Error enviando WebPush de reprogramación: {}", ex.getMessage(), ex);
        }

        // 2. Alerta al WhatsApp de la administradora con link para contactar
        try {
            String targetAdminPhone = resolveAdminWhatsAppNumber();
            if (targetAdminPhone != null && !targetAdminPhone.isBlank()) {
                String contactUrl = whatsAppHandoffService.generateWhatsAppUrl(customerPhone);
                String adminAlertMessage = String.format(
                        "🔄 *SOLICITUD DE REPROGRAMACIÓN - BunnyCure*\n\n" +
                        "La clienta *%s* ha solicitado reprogramar su cita.\n\n" +
                        "📋 *Servicio:* %s\n" +
                        "📅 *Fecha actual:* %s\n" +
                        "⏰ *Hora actual:* %s\n\n" +
                        "📱 *Contactar a clienta:*\n%s",
                        customerName, serviceName, fecha, hora, contactUrl
                );
                whatsAppService.sendTextMessage(targetAdminPhone, adminAlertMessage);
                log.info("[WEBHOOK] 📲 Alerta WhatsApp admin enviada a: {}", targetAdminPhone);
            }
        } catch (Exception ex) {
            log.error("[WEBHOOK] ❌ Error enviando alerta WhatsApp admin de reprogramación: {}", ex.getMessage(), ex);
        }
    }

    private Optional<Appointment> findAppointmentToConfirm(WhatsAppWebhookDto.Message message) {
        Optional<Long> payloadAppointmentId = extractAppointmentId(message);
        if (payloadAppointmentId.isPresent()) {
            return appointmentRepository.findByIdWithDetails(payloadAppointmentId.get())
                    .filter(a -> a.getStatus() == AppointmentStatus.PENDING || a.getStatus() == AppointmentStatus.CONFIRMED);
        }

        LocalDate today = getTodayInConfiguredZone();
        String from = message.getFrom();

        return appointmentRepository.findByStatus(AppointmentStatus.PENDING).stream()
                .filter(a -> a.getAppointmentDate() != null && !a.getAppointmentDate().isBefore(today))
                .filter(a -> a.getCustomer() != null)
                .filter(a -> matchesPhone(a.getCustomer().getPhone(), from))
                .findFirst();
    }

    private Optional<Appointment> findAppointmentToReschedule(WhatsAppWebhookDto.Message message) {
        Optional<Long> payloadAppointmentId = extractAppointmentId(message);
        if (payloadAppointmentId.isPresent()) {
            return appointmentRepository.findByIdWithDetails(payloadAppointmentId.get())
                    .filter(a -> a.getStatus() == AppointmentStatus.PENDING
                            || a.getStatus() == AppointmentStatus.CONFIRMED
                            || a.getStatus() == AppointmentStatus.RESCHEDULE_REQUESTED);
        }

        LocalDate today = getTodayInConfiguredZone();
        String from = message.getFrom();

        return appointmentRepository.findAll().stream()
                .filter(a -> a.getStatus() == AppointmentStatus.PENDING
                        || a.getStatus() == AppointmentStatus.CONFIRMED
                        || a.getStatus() == AppointmentStatus.RESCHEDULE_REQUESTED)
                .filter(a -> a.getAppointmentDate() != null && !a.getAppointmentDate().isBefore(today))
                .filter(a -> a.getCustomer() != null)
                .filter(a -> matchesPhone(a.getCustomer().getPhone(), from))
                .findFirst();
    }

    private Optional<Long> extractAppointmentId(WhatsAppWebhookDto.Message message) {
        if (message.getButton() != null && message.getButton().getPayload() != null) {
            Optional<Long> fromButton = extractAppointmentIdFromToken(message.getButton().getPayload());
            if (fromButton.isPresent()) {
                return fromButton;
            }
        }

        if (message.getInteractive() != null) {
            if (message.getInteractive().getButtonReply() != null) {
                Optional<Long> fromInteractiveButton = extractAppointmentIdFromToken(message.getInteractive().getButtonReply().getId());
                if (fromInteractiveButton.isPresent()) {
                    return fromInteractiveButton;
                }
            }
            if (message.getInteractive().getListReply() != null) {
                return extractAppointmentIdFromToken(message.getInteractive().getListReply().getId());
            }
        }

        return Optional.empty();
    }

    private Optional<Long> extractAppointmentIdFromToken(String rawValue) {
        if (rawValue == null) {
            return Optional.empty();
        }

        String payload = rawValue.trim();
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

    public String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }
        return phone.replaceAll("\\D", "");
    }

    public boolean matchesPhone(String p1, String p2) {
        String n1 = normalizePhone(p1);
        String n2 = normalizePhone(p2);
        if (n1.isEmpty() || n2.isEmpty()) {
            return false;
        }
        if (n1.equals(n2)) {
            return true;
        }
        // Normalize Chilean prefix 56 (e.g. 56912345678 -> 912345678)
        String tail1 = (n1.startsWith("56") && n1.length() == 11) ? n1.substring(2) : n1;
        String tail2 = (n2.startsWith("56") && n2.length() == 11) ? n2.substring(2) : n2;
        if (tail1.equals(tail2)) {
            return true;
        }
        if (tail1.endsWith(tail2) || tail2.endsWith(tail1)) {
            int minLen = Math.min(tail1.length(), tail2.length());
            if (minLen >= 8) {
                return true;
            }
        }
        return false;
    }

    public ZoneId getConfiguredZoneId() {
        try {
            String timezone = appSettingsService.get("app.timezone", "America/Santiago");
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            return ZoneId.of("America/Santiago");
        }
    }

    public LocalDate getTodayInConfiguredZone() {
        return LocalDate.now(getConfiguredZoneId());
    }

    private boolean isCustomerRecordOwnerMessage(WhatsAppWebhookDto.Message message) {
        String from = message != null ? message.getFrom() : null;
        if (from == null || from.isBlank() || customerRecordAuthorizedNumbers == null || customerRecordAuthorizedNumbers.isBlank()) {
            log.warn("[WEBHOOK] ⚠️ Owner check: sin números autorizados configurados o sender vacío. sender='{}'", from);
            return false;
        }
        boolean authorized = java.util.Arrays.stream(customerRecordAuthorizedNumbers.split(","))
                .map(String::trim)
                .filter(n -> !n.isBlank())
                .anyMatch(n -> matchesPhone(n, from));
        log.info("[WEBHOOK] 🔐 Owner check: authorized='{}' sender='{}' match={}",
                customerRecordAuthorizedNumbers, from, authorized);
        return authorized;
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String safePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "n/a";
        }
        return value.substring(0, Math.min(value.length(), 8));
    }

    private boolean isHandoffEnabled() {
        try {
            return appSettingsService.isWhatsappHandoffEnabled();
        } catch (Exception ex) {
            log.warn("[WEBHOOK] ⚠️ No se pudo leer configuración de handoff, se usará habilitado por defecto");
            return true;
        }
    }

    private void sendHandoffMessage(String toPhoneNumber, String trigger) {
        String handoffMessage = whatsAppHandoffService.buildClientHandoffMessage();
        String handoffLink = whatsAppHandoffService.buildHumanChannelLink();

        String finalMessage = handoffMessage;
        if (handoffLink != null && !handoffLink.isBlank() && (handoffMessage == null || !handoffMessage.contains(handoffLink))) {
            finalMessage = (handoffMessage != null ? handoffMessage : "") + "\n" + handoffLink;
        }
        if (finalMessage == null || finalMessage.isBlank()) {
            finalMessage = "Para ayudarte mejor, escribe a nuestro canal de atención humana.";
        }

        log.info("[WEBHOOK] 🤝 Derivando a atención humana. trigger={}, to={}", trigger, toPhoneNumber);
        whatsAppService.sendTextMessage(toPhoneNumber, finalMessage.trim());
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
                    logFailedStatusErrors(status);
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

    private void logFailedStatusErrors(WhatsAppWebhookDto.Status status) {
        if (status == null || status.getErrors() == null || status.getErrors().isEmpty()) {
            log.error("[WEBHOOK] ❌ status=failed sin campo errors en payload");
            return;
        }

        for (WhatsAppWebhookDto.StatusError error : status.getErrors()) {
            String details = error.getErrorData() != null ? error.getErrorData().getDetails() : null;
            log.error("[WEBHOOK] ❌ Meta error code={}, title={}, message={}, details={}, href={}",
                    error.getCode(),
                    error.getTitle(),
                    error.getMessage(),
                    details,
                    error.getHref());
        }
    }

    private boolean isDuplicateEvent(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long previous = processedEventIds.putIfAbsent(eventId, now);
        if (previous != null && (now - previous) < DEDUPE_TTL_MILLIS) {
            return true;
        }

        if (isAlreadyProcessedInDatabase(eventId)) {
            processedEventIds.put(eventId, now);
            cleanupOldEvents(now);
            cleanupExpiredPersistedEventsIfNeeded(now);
            return true;
        }

        cleanupOldEvents(now);
        cleanupExpiredPersistedEventsIfNeeded(now);
        return false;
    }

    private boolean isAlreadyProcessedInDatabase(String eventId) {
        java.time.LocalDateTime processedAt = java.time.LocalDateTime.now();
        WebhookProcessedEvent event = WebhookProcessedEvent.builder()
                .eventId(eventId)
                .processedAt(processedAt)
                .expiresAt(processedAt.plusNanos(DEDUPE_TTL_MILLIS * 1_000_000L))
                .build();

        try {
            webhookProcessedEventRepository.save(event);
            return false;
        } catch (DataIntegrityViolationException duplicate) {
            log.debug("[WEBHOOK] ♻️ Duplicate event detected in DB id={}", eventId);
            return true;
        }
    }

    private void cleanupOldEvents(long now) {
        if (processedEventIds.size() < 5000) {
            return;
        }
        processedEventIds.entrySet().removeIf(entry -> (now - entry.getValue()) > DEDUPE_TTL_MILLIS);
    }

    private void cleanupExpiredPersistedEventsIfNeeded(long nowMillis) {
        long checks = dedupeChecksCounter.incrementAndGet();
        if (checks % DEDUPE_CLEANUP_EVERY_EVENTS != 0) {
            return;
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        long deleted = webhookProcessedEventRepository.deleteByExpiresAtBefore(now);
        if (deleted > 0) {
            log.info("[WEBHOOK] 🧹 Deleted {} expired persisted webhook dedupe event(s)", deleted);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void handleOperationalWebhookEvent(String field, WhatsAppWebhookDto.Value value, boolean notifyAdmin) {
        long count = operationalEventCounters.merge(field, 1L, Long::sum);
        String phoneId = value != null && value.getMetadata() != null ? value.getMetadata().getPhoneNumberId() : "unknown";

        if (notifyAdmin) {
            log.warn("[WEBHOOK] ⚠️ Evento operacional: {} (count={}, phoneId={})", field, count, phoneId);
        } else {
            log.info("[WEBHOOK] 📋 Evento operacional: {} (count={}, phoneId={})", field, count, phoneId);
        }
        log.debug("[WEBHOOK] Valor evento {}: {}", field, value);

        persistOperationalEvent(field, phoneId, count, notifyAdmin, value);

        if (notifyAdmin) {
            maybeNotifyAdmin(field, phoneId, count);
        }
    }

    private void persistOperationalEvent(String field,
                                         String phoneId,
                                         long count,
                                         boolean riskEvent,
                                         WhatsAppWebhookDto.Value value) {
        try {
            String payloadSummary = buildPayloadSummary(value);
            WebhookOperationalEvent event = WebhookOperationalEvent.builder()
                    .eventType(field)
                    .phoneNumberId(phoneId)
                    .riskEvent(riskEvent)
                    .occurrenceCount(count)
                    .payloadSummary(payloadSummary)
                    .build();
            webhookOperationalEventRepository.save(event);
        } catch (Exception ex) {
            log.warn("[WEBHOOK] ⚠️ No se pudo persistir evento operacional {}: {}", field, ex.getMessage());
        }
    }

    private String buildPayloadSummary(WhatsAppWebhookDto.Value value) {
        if (value == null) {
            return "value=null";
        }

        int messagesCount = value.getMessages() != null ? value.getMessages().size() : 0;
        int statusesCount = value.getStatuses() != null ? value.getStatuses().size() : 0;
        String messagingProduct = value.getMessagingProduct() != null ? value.getMessagingProduct() : "unknown";
        String displayPhone = value.getMetadata() != null && value.getMetadata().getDisplayPhoneNumber() != null
                ? value.getMetadata().getDisplayPhoneNumber()
                : "unknown";

        String extraSummary = summarizeExtraFields(value.getExtraFields());
        String summary = String.format("product=%s,displayPhone=%s,messages=%d,statuses=%d,extras=%s",
                messagingProduct, displayPhone, messagesCount, statusesCount, extraSummary);
        return summary.length() > 500 ? summary.substring(0, 500) : summary;
    }

    private String summarizeExtraFields(Map<String, Object> extraFields) {
        if (extraFields == null || extraFields.isEmpty()) {
            return "none";
        }
        String raw = extraFields.toString().replaceAll("\\s+", " ").trim();
        if (raw.length() <= 240) {
            return raw;
        }
        return raw.substring(0, 240) + "...";
    }

    private boolean isTemplateStatusRisk(WhatsAppWebhookDto.Value value) {
        if (value == null || value.getExtraFields() == null || value.getExtraFields().isEmpty()) {
            return false;
        }

        String raw = value.getExtraFields().toString().toLowerCase(Locale.ROOT);
        return raw.contains("rejected")
                || raw.contains("reject")
                || raw.contains("paused")
                || raw.contains("disabled")
                || raw.contains("blocked");
    }

    private void maybeNotifyAdmin(String field, String phoneId, long count) {
        if (!alertAdminOnRiskEvents || adminWhatsAppNumber == null || adminWhatsAppNumber.isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();
        Long previousAlert = lastAlertByKey.putIfAbsent(field, now);
        if (previousAlert != null && (now - previousAlert) < ALERT_THROTTLE_MILLIS) {
            return;
        }
        lastAlertByKey.put(field, now);

        String message = String.format(
                "[BunnyCure] Alerta webhook: %s (phoneId=%s, ocurrencias=%d en esta instancia).",
                field,
                phoneId,
                count
        );
        whatsAppService.sendTextMessage(resolveAdminWhatsAppNumber(), message);
    }

    private String resolveAdminWhatsAppNumber() {
        if (appSettingsService == null) {
            return adminWhatsAppNumber;
        }
        try {
            String configured = appSettingsService.getAdminAlertWhatsappNumber(adminWhatsAppNumber);
            return (configured != null && !configured.isBlank()) ? configured : adminWhatsAppNumber;
        } catch (Exception ex) {
            return adminWhatsAppNumber;
        }
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

            if (isConfirmPayload(reply.getTitle(), reply.getId())) {
                handleConfirmAttendance(message);
                return;
            }

            if (isReschedulePayload(reply.getTitle(), reply.getId())) {
                handleRescheduleRequest(message);
                return;
            }
        }

        if (message.getInteractive().getListReply() != null) {
            var reply = message.getInteractive().getListReply();
            log.info("[WEBHOOK] 📋 List reply id: {}", reply.getId());
            log.info("[WEBHOOK] 📋 List reply title: {}", reply.getTitle());
            log.info("[WEBHOOK] 📋 List reply description: {}", reply.getDescription());

            if (isConfirmPayload(reply.getTitle(), reply.getId())) {
                handleConfirmAttendance(message);
                return;
            }

            if (isReschedulePayload(reply.getTitle(), reply.getId())) {
                handleRescheduleRequest(message);
                return;
            }
        }

        if (message.getFrom() != null && !message.getFrom().isBlank()) {
            if (isHandoffEnabled()) {
                sendHandoffMessage(message.getFrom(), "interactive_unmapped");
            } else {
                whatsAppService.sendTextMessage(
                        message.getFrom(),
                        "Gracias por tu respuesta. Si necesitas ayuda con tu cita, escribe CONFIRMAR ASISTENCIA."
                );
            }
        }
    }
}