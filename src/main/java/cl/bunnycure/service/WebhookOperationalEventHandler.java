package cl.bunnycure.service;

import cl.bunnycure.domain.model.WebhookOperationalEvent;
import cl.bunnycure.domain.repository.WebhookOperationalEventRepository;
import cl.bunnycure.web.dto.WhatsAppWebhookDto;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Eventos operativos del webhook de WhatsApp (calidad de cuenta, estado de plantillas, etc.):
 * contadores, registro persistido y alerta al administrador con límite de frecuencia.
 * Extraído de WhatsAppWebhookService; la configuración se lee al usarla (suppliers).
 */
@Slf4j
class WebhookOperationalEventHandler {

    private static final long ALERT_THROTTLE_MILLIS = 5 * 60 * 1000L;

    private final Map<String, Long> lastAlertByKey = new ConcurrentHashMap<>();
    private final Map<String, Long> operationalEventCounters = new ConcurrentHashMap<>();
    private final WebhookOperationalEventRepository webhookOperationalEventRepository;
    private final WhatsAppService whatsAppService;
    private final Supplier<Boolean> alertAdminOnRiskEvents;
    private final Supplier<String> configuredAdminNumber;
    private final Supplier<String> resolvedAdminNumber;

    WebhookOperationalEventHandler(WebhookOperationalEventRepository webhookOperationalEventRepository,
                                   WhatsAppService whatsAppService,
                                   Supplier<Boolean> alertAdminOnRiskEvents,
                                   Supplier<String> configuredAdminNumber,
                                   Supplier<String> resolvedAdminNumber) {
        this.webhookOperationalEventRepository = webhookOperationalEventRepository;
        this.whatsAppService = whatsAppService;
        this.alertAdminOnRiskEvents = alertAdminOnRiskEvents;
        this.configuredAdminNumber = configuredAdminNumber;
        this.resolvedAdminNumber = resolvedAdminNumber;
    }

    void handle(String field, WhatsAppWebhookDto.Value value, boolean notifyAdmin) {
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

    boolean isTemplateStatusRisk(WhatsAppWebhookDto.Value value) {
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
        String adminWhatsAppNumber = configuredAdminNumber.get();
        if (!alertAdminOnRiskEvents.get() || adminWhatsAppNumber == null || adminWhatsAppNumber.isBlank()) {
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
        whatsAppService.sendTextMessage(resolvedAdminNumber.get(), message);
    }
}
