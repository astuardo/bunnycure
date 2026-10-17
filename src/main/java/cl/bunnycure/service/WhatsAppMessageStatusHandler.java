package cl.bunnycure.service;

import cl.bunnycure.web.dto.WhatsAppWebhookDto;
import lombok.extern.slf4j.Slf4j;
import java.util.function.Supplier;

/**
 * Estados de entrega de los mensajes enviados (sent, delivered, read, failed): deduplica,
 * actualiza el registro de notificaciones y deja trazas de los errores de Meta.
 * Extraído de WhatsAppWebhookService.
 */
@Slf4j
class WhatsAppMessageStatusHandler {

    private final WebhookEventDeduplicator deduplicator;
    private final Supplier<NotificationLogService> notificationLogSupplier;

    WhatsAppMessageStatusHandler(WebhookEventDeduplicator deduplicator, Supplier<NotificationLogService> notificationLogSupplier) {
        this.deduplicator = deduplicator;
        this.notificationLogSupplier = notificationLogSupplier;
    }

    void processMessageStatuses(java.util.List<WhatsAppWebhookDto.Status> statuses) {
        NotificationLogService notificationLogService = notificationLogSupplier.get();
        log.info("[WEBHOOK] 📊 Procesando {} estado(s) de mensaje(s)", statuses.size());

        for (WhatsAppWebhookDto.Status status : statuses) {
            String dedupeKey = "status:" + status.getId() + ":" + (status.getStatus() != null ? status.getStatus() : "");
            if (deduplicator.isDuplicate(dedupeKey)) {
                log.info("[WEBHOOK] ♻️ Status already processed, skipping id={}, status={}", status.getId(), status.getStatus());
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

            if (notificationLogService != null && status.getId() != null && status.getStatus() != null) {
                try {
                    notificationLogService.updateStatusByWamid(status.getId(), status.getStatus());
                } catch (Exception e) {
                    log.warn("[WEBHOOK] Error actualizando status de log para wamid {}: {}", status.getId(), e.getMessage());
                }
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
}
