package cl.bunnycure.service;

import cl.bunnycure.domain.repository.WebhookOperationalEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class WebhookOperationalEventMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(WebhookOperationalEventMaintenanceService.class);

    private final WebhookOperationalEventRepository webhookOperationalEventRepository;

    @Value("${whatsapp.webhook.operational-events.cleanup-enabled:true}")
    private boolean cleanupEnabled;

    @Value("${whatsapp.webhook.operational-events.retention-days:30}")
    private int retentionDays;

    public WebhookOperationalEventMaintenanceService(WebhookOperationalEventRepository webhookOperationalEventRepository) {
        this.webhookOperationalEventRepository = webhookOperationalEventRepository;
    }

    @Scheduled(cron = "${whatsapp.webhook.operational-events.cleanup-cron:0 15 * * * *}")
    public void cleanupOldOperationalEvents() {
        if (!cleanupEnabled) {
            return;
        }
        if (retentionDays < 1) {
            log.warn("[WEBHOOK-MAINT] Retention invalida para eventos operacionales: {}", retentionDays);
            return;
        }

        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
            long deleted = webhookOperationalEventRepository.deleteByCreatedAtBefore(threshold);
            if (deleted > 0) {
                log.info("[WEBHOOK-MAINT] Deleted {} webhook operational events older than {} day(s)", deleted, retentionDays);
            }
        } catch (Exception ex) {
            log.error("[WEBHOOK-MAINT] Error deleting old webhook operational events", ex);
        }
    }
}
