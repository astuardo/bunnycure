package cl.bunnycure.service;

import cl.bunnycure.domain.enums.OutboxStatus;
import cl.bunnycure.domain.repository.WhatsAppAdminAlertOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppAdminAlertOutboxMaintenanceService {

    private static final Set<OutboxStatus> CLEANUP_STATUSES = Set.of(OutboxStatus.SENT, OutboxStatus.FAILED);

    private final WhatsAppAdminAlertOutboxRepository outboxRepository;

    @Value("${bunnycure.whatsapp.admin-alert.outbox.cleanup-enabled:true}")
    private boolean cleanupEnabled;

    @Value("${bunnycure.whatsapp.admin-alert.outbox.retention-days:30}")
    private int retentionDays;

    @Scheduled(cron = "${bunnycure.whatsapp.admin-alert.outbox.cleanup-cron:0 20 * * * *}")
    public void cleanupOldOutboxRows() {
        if (!cleanupEnabled) {
            return;
        }
        if (retentionDays < 1) {
            log.warn("[WHATSAPP-ADMIN-OUTBOX] Retention invalida: {}", retentionDays);
            return;
        }

        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
            long deleted = outboxRepository.deleteByStatusInAndCreatedAtBefore(CLEANUP_STATUSES, threshold);
            if (deleted > 0) {
                log.info("[WHATSAPP-ADMIN-OUTBOX] Eliminadas {} filas antiguas del outbox", deleted);
            }
        } catch (Exception ex) {
            log.error("[WHATSAPP-ADMIN-OUTBOX] Error limpiando outbox", ex);
        }
    }
}
