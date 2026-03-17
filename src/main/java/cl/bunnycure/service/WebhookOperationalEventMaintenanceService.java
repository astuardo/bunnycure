package cl.bunnycure.service;

import cl.bunnycure.domain.repository.WebhookOperationalEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookOperationalEventMaintenanceService {

    private final WebhookOperationalEventRepository webhookOperationalEventRepository;

    @Value("${whatsapp.webhook.operational-events.cleanup-enabled:true}")
    private boolean cleanupEnabled;

    @Value("${whatsapp.webhook.operational-events.retention-days:30}")
    private int retentionDays;

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
