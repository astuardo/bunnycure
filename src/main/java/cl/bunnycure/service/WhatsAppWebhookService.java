package cl.bunnycure.service;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.WebhookOperationalEventRepository;
import cl.bunnycure.domain.repository.WebhookProcessedEventRepository;
import cl.bunnycure.web.dto.WhatsAppWebhookDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio para procesar las notificaciones de webhook de WhatsApp
 */
@Slf4j
@Service
public class WhatsAppWebhookService {
    private static final Pattern APPOINTMENT_ID_PATTERN = Pattern.compile("(?:^|[:#_\\-])(\\d+)$");

    private final AppointmentRepository appointmentRepository;
    private final WebhookOperationalEventRepository webhookOperationalEventRepository;
    private final WebhookProcessedEventRepository webhookProcessedEventRepository;
    private final WhatsAppService whatsAppService;
    private final AppSettingsService appSettingsService;
    private final WhatsAppHandoffService whatsAppHandoffService;
    private final CustomerServiceRecordService customerServiceRecordService;
    private final WebPushNotificationService webPushNotificationService;

    /** Colaboradores internos (mismo paquete), construidos con las dependencias del servicio. */
    private final WebhookEventDeduplicator deduplicator;
    private final WebhookOperationalEventHandler operationalEvents;
    private final WhatsAppAdminTemplateCommands adminTemplateCommands;
    private final WhatsAppMessageStatusHandler messageStatuses;

    public WhatsAppWebhookService(AppointmentRepository appointmentRepository,
                                  WebhookOperationalEventRepository webhookOperationalEventRepository,
                                  WebhookProcessedEventRepository webhookProcessedEventRepository,
                                  WhatsAppService whatsAppService,
                                  AppSettingsService appSettingsService,
                                  WhatsAppHandoffService whatsAppHandoffService,
                                  CustomerServiceRecordService customerServiceRecordService,
                                  WebPushNotificationService webPushNotificationService) {
        this.appointmentRepository = appointmentRepository;
        this.webhookOperationalEventRepository = webhookOperationalEventRepository;
        this.webhookProcessedEventRepository = webhookProcessedEventRepository;
        this.whatsAppService = whatsAppService;
        this.appSettingsService = appSettingsService;
        this.whatsAppHandoffService = whatsAppHandoffService;
        this.customerServiceRecordService = customerServiceRecordService;
        this.webPushNotificationService = webPushNotificationService;
        this.deduplicator = new WebhookEventDeduplicator(webhookProcessedEventRepository);
        // Suppliers: los @Value se inyectan después del constructor y los tests los ajustan por reflexión
        this.operationalEvents = new WebhookOperationalEventHandler(webhookOperationalEventRepository, whatsAppService,
                () -> alertAdminOnRiskEvents, () -> adminWhatsAppNumber, this::resolveAdminWhatsAppNumber);
        this.adminTemplateCommands = new WhatsAppAdminTemplateCommands(whatsAppService,
                () -> marketingTemplateAiService, () -> marketingTemplateRepository,
                () -> frontendBaseUrl, this::resolveAdminWhatsAppNumber);
        this.messageStatuses = new WhatsAppMessageStatusHandler(deduplicator, () -> notificationLogService);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private cl.bunnycure.service.marketing.MarketingTemplateAiService marketingTemplateAiService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private cl.bunnycure.domain.repository.MarketingTemplateRepository marketingTemplateRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private IncomingWhatsAppMessageService incomingWhatsAppMessageService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NotificationLogService notificationLogService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private cl.bunnycure.domain.repository.TenantRepository tenantRepository;

    @Value("${bunnycure.whatsapp.number:}")
    private String adminWhatsAppNumber;

    @Value("${whatsapp.webhook.alert-admin:false}")
    private boolean alertAdminOnRiskEvents;

    @Value("${whatsapp.webhook.customer-record.authorized-numbers:}")
    private String customerRecordAuthorizedNumbers;

    @Value("${app.frontend.base-url:https://bunnycure-frontend.vercel.app}")
    private String frontendBaseUrl;

    public void setNotificationLogService(NotificationLogService notificationLogService) {
        this.notificationLogService = notificationLogService;
    }

    public void setMarketingTemplateAiService(cl.bunnycure.service.marketing.MarketingTemplateAiService marketingTemplateAiService) {
        this.marketingTemplateAiService = marketingTemplateAiService;
    }

    public void setMarketingTemplateRepository(cl.bunnycure.domain.repository.MarketingTemplateRepository marketingTemplateRepository) {
        this.marketingTemplateRepository = marketingTemplateRepository;
    }

    public void setIncomingWhatsAppMessageService(IncomingWhatsAppMessageService incomingWhatsAppMessageService) {
        this.incomingWhatsAppMessageService = incomingWhatsAppMessageService;
    }

    public void setTenantRepository(cl.bunnycure.domain.repository.TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
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
            processChangeInOwningTenant(change);
        }
    }

    /**
     * El webhook llega como petición pública (sin salón resuelto). Cada cambio trae el
     * phone_number_id del número de WhatsApp que recibió el evento: se procesa en el contexto
     * del salón dueño de ese número, y el contexto previo se restaura al terminar.
     */
    private void processChangeInOwningTenant(WhatsAppWebhookDto.Change change) {
        String phoneNumberId = change.getValue() != null && change.getValue().getMetadata() != null
                ? change.getValue().getMetadata().getPhoneNumberId()
                : null;
        Long previousTenantId = cl.bunnycure.config.TenantContext.getExplicitTenantId();
        try {
            cl.bunnycure.config.TenantContext.setCurrentTenantId(resolveTenantIdForPhoneNumberId(phoneNumberId));
            processChange(change);
        } finally {
            cl.bunnycure.config.TenantContext.setCurrentTenantId(previousTenantId);
        }
    }

    /**
     * Salón dueño del número de WhatsApp. Si ningún salón lo tiene configurado, es el número
     * de la plataforma (WhatsAppConfig), que pertenece al salón principal.
     */
    Long resolveTenantIdForPhoneNumberId(String phoneNumberId) {
        if (phoneNumberId == null || phoneNumberId.isBlank() || tenantRepository == null) {
            return cl.bunnycure.config.TenantContext.DEFAULT_TENANT_ID;
        }
        return tenantRepository.findFirstByWhatsappPhoneIdOrderByIdAsc(phoneNumberId.trim())
                .map(cl.bunnycure.domain.model.Tenant::getId)
                .orElse(cl.bunnycure.config.TenantContext.DEFAULT_TENANT_ID);
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
                    messageStatuses.processMessageStatuses(value.getStatuses());
                }
                break;
                
            case "message_template_status_update":
                adminTemplateCommands.handleTemplateStatusUpdate(value);
                operationalEvents.handle("message_template_status_update", value, operationalEvents.isTemplateStatusRisk(value));
                break;
                
            case "message_template_quality_update":
                operationalEvents.handle("message_template_quality_update", value, true);
                break;
                
            case "phone_number_name_update":
                log.info("[WEBHOOK] 📱 Actualización de nombre del número de teléfono");
                log.debug("[WEBHOOK] Valor: {}", value);
                break;
                
            case "phone_number_quality_update":
                operationalEvents.handle("phone_number_quality_update", value, true);
                break;
                
            case "account_alerts":
                operationalEvents.handle("account_alerts", value, true);
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
            if (deduplicator.isDuplicate("msg:" + message.getId())) {
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
                    processTextMessage(message, contactName);
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
                    processButtonMessage(message, contactName);
                    break;

                case "interactive":
                    processInteractiveMessage(message, contactName);
                    break;
                
                default:
                    log.info("[WEBHOOK] ❓ Tipo de mensaje no manejado: {}", message.getType());
            }
        }
    }

    private void processTextMessage(WhatsAppWebhookDto.Message message, String contactName) {
        if (message.getText() == null || message.getText().getBody() == null) {
            return;
        }

        String text = message.getText().getBody().trim();
        log.info("[WEBHOOK] 💬 Texto: {}", text);
        if (text.isEmpty() || message.getFrom() == null || message.getFrom().isBlank()) {
            return;
        }

        // Registrar mensaje entrante en la bandeja de BunnyCure y disparar notificación push
        if (incomingWhatsAppMessageService != null) {
            try {
                incomingWhatsAppMessageService.saveIncomingMessage(
                        message.getId(),
                        message.getFrom(),
                        contactName,
                        text,
                        "text"
                );
            } catch (Exception e) {
                log.error("[WEBHOOK] ❌ Error guardando mensaje entrante en bandeja: {}", e.getMessage(), e);
            }
        }

        // Si es la administradora/dueña y solicita crear una plantilla de marketing
        if (isCustomerRecordOwnerMessage(message) && adminTemplateCommands.isMarketingTemplateAiCommand(text)) {
            adminTemplateCommands.handleAdminAiTemplateCreation(message, text);
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

    private void processButtonMessage(WhatsAppWebhookDto.Message message, String contactName) {
        if (message.getButton() == null) {
            log.warn("[WEBHOOK] ⚠️ Mensaje tipo button sin contenido button");
            return;
        }

        String text = message.getButton().getText();
        String payload = message.getButton().getPayload();
        log.info("[WEBHOOK] 🔘 Button text: {}", text);
        log.info("[WEBHOOK] 🔘 Button payload: {}", payload);

        // Guardar botón presionado en la bandeja de entrada de WhatsApp
        if (incomingWhatsAppMessageService != null) {
            try {
                String content = (text != null && !text.isBlank()) ? text : (payload != null ? payload : "Botón presionado");
                incomingWhatsAppMessageService.saveIncomingMessage(
                        message.getId(),
                        message.getFrom(),
                        contactName,
                        "🔘 " + content,
                        "button"
                );
            } catch (Exception e) {
                log.error("[WEBHOOK] ❌ Error guardando botón en bandeja: {}", e.getMessage(), e);
            }
        }

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

        if (normalizedPayload.contains("reprogramar")
                || normalizedPayload.contains("reagendar")
                || normalizedPayload.contains("cambiar_hora")
                || normalizedPayload.contains("cambiar_cita")
                || normalizedPayload.contains("cambiar_fecha")
                || normalizedPayload.contains("cancelar")
                || normalizedPayload.contains("anular")) {
            return true;
        }

        return normalizedText.contains("cancelar")
                || normalizedText.contains("cancelo")
                || normalizedText.contains("anular")
                || normalizedText.contains("anulo")
                || normalizedText.contains("reprogramar")
                || normalizedText.contains("reagendar")
                || normalizedText.contains("cambiar hora")
                || normalizedText.contains("cambiar cita")
                || normalizedText.contains("cambiar fecha")
                || normalizedText.contains("cambiar dia")
                || normalizedText.contains("cambiar día")
                || normalizedText.contains("modificar cita")
                || normalizedText.contains("modificar hora")
                || normalizedText.contains("otra hora")
                || normalizedText.contains("otro dia")
                || normalizedText.contains("otro día")
                || normalizedText.contains("otra fecha")
                || normalizedText.contains("no podre")
                || normalizedText.contains("no podré")
                || normalizedText.contains("no puedo")
                || normalizedText.contains("no alcanzo")
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
        log.info("[WEBHOOK] ✅ Cita confirmada desde button/texto. appointmentId={}", target.getId());

        // Registrar en historial de notificaciones
        if (notificationLogService != null) {
            notificationLogService.logIncomingCustomerAction(
                    target,
                    from,
                    "Confirmación de Asistencia",
                    "La clienta confirmó su asistencia a la cita.",
                    message.getId()
            );
        }

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

            // Alertar a la administradora de la solicitud no vinculada automáticamente
            try {
                String targetAdminPhone = resolveAdminWhatsAppNumber();
                if (targetAdminPhone != null && !targetAdminPhone.isBlank()) {
                    String contactUrl = whatsAppHandoffService.generateWhatsAppUrl(from);
                    String adminAlertMessage = String.format(
                            "⚠️ *SOLICITUD DE REPROGRAMACIÓN SIN CITA VINCULADA - BunnyCure*\n\n" +
                            "El número %s solicitó cancelar/reprogramar, pero no se encontró una cita futura activa asociada automáticamente.\n\n" +
                            "📱 *Contactar a clienta:*\n%s",
                            from, contactUrl
                    );
                    whatsAppService.sendTextMessage(targetAdminPhone, adminAlertMessage);
                }
            } catch (Exception ex) {
                log.error("[WEBHOOK] Error enviando alerta admin de reprogramación huérfana: {}", ex.getMessage());
            }
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

        // 1. Guardar traza en el historial de notificaciones de la cita
        if (notificationLogService != null) {
            notificationLogService.logIncomingCustomerAction(
                    appointment,
                    customerPhone,
                    "Solicitud de Reprogramación / Cancelación",
                    "La clienta solicitó reprogramar o cancelar su cita vía WhatsApp.",
                    message.getId()
            );
        }

        // 2. Notificación Web Push a la App (para que salte en el panel admin)
        try {
            String pushTitle = "🔄 Cita necesita ser reprogramada";
            String pushBody = String.format("%s solicitó reprogramar su cita del %s a las %s (%s).",
                    customerName, fecha, hora, serviceName);
            webPushNotificationService.sendAdminCustomNotification(pushTitle, pushBody, "/appointments?status=RESCHEDULE_REQUESTED");
            log.info("[WEBHOOK] 📲 Notificación Web Push de reprogramación enviada exitosamente para cita ID: {}", appointment.getId());
        } catch (Exception ex) {
            log.error("[WEBHOOK] ❌ Error enviando WebPush de reprogramación: {}", ex.getMessage(), ex);
        }

        // 3. Alerta al WhatsApp de la administradora con link para contactar
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

    private void processInteractiveMessage(WhatsAppWebhookDto.Message message, String contactName) {
        if (message.getInteractive() == null) {
            log.warn("[WEBHOOK] ⚠️ Mensaje tipo interactive sin contenido interactive");
            return;
        }

        log.info("[WEBHOOK] 🧩 Interactive type: {}", message.getInteractive().getType());

        if (message.getInteractive().getButtonReply() != null) {
            var reply = message.getInteractive().getButtonReply();
            log.info("[WEBHOOK] 🔘 Button reply id: {}", reply.getId());
            log.info("[WEBHOOK] 🔘 Button reply title: {}", reply.getTitle());

            // Guardar botón interactivo en bandeja de entrada
            if (incomingWhatsAppMessageService != null) {
                try {
                    String content = (reply.getTitle() != null && !reply.getTitle().isBlank()) ? reply.getTitle() : reply.getId();
                    incomingWhatsAppMessageService.saveIncomingMessage(
                            message.getId(),
                            message.getFrom(),
                            contactName,
                            "🔘 " + content,
                            "interactive"
                    );
                } catch (Exception e) {
                    log.error("[WEBHOOK] ❌ Error guardando interactive reply en bandeja: {}", e.getMessage(), e);
                }
            }

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

            // Guardar lista interactiva en bandeja de entrada
            if (incomingWhatsAppMessageService != null) {
                try {
                    String content = (reply.getTitle() != null && !reply.getTitle().isBlank()) ? reply.getTitle() : reply.getId();
                    incomingWhatsAppMessageService.saveIncomingMessage(
                            message.getId(),
                            message.getFrom(),
                            contactName,
                            "📋 " + content,
                            "interactive"
                    );
                } catch (Exception e) {
                    log.error("[WEBHOOK] ❌ Error guardando list reply en bandeja: {}", e.getMessage(), e);
                }
            }

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