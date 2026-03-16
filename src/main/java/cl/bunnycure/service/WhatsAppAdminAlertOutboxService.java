package cl.bunnycure.service;

import cl.bunnycure.domain.enums.OutboxStatus;
import cl.bunnycure.domain.model.BookingRequest;
import cl.bunnycure.domain.model.WhatsAppAdminAlertOutbox;
import cl.bunnycure.domain.repository.BookingRequestRepository;
import cl.bunnycure.domain.repository.WhatsAppAdminAlertOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class WhatsAppAdminAlertOutboxService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppAdminAlertOutboxService.class);
    private static final Set<OutboxStatus> DUE_STATUSES = Set.of(OutboxStatus.PENDING, OutboxStatus.RETRY);

    private final WhatsAppAdminAlertOutboxRepository outboxRepository;
    private final BookingRequestRepository bookingRequestRepository;
    private final WhatsAppService whatsAppService;
    private final AppSettingsService appSettingsService;

    @Value("${bunnycure.whatsapp.admin-alert.enabled:true}")
    private boolean adminAlertEnabled;

    @Value("${bunnycure.whatsapp.admin-alert-number:56964499995}")
    private String adminAlertNumberFallback;

    @Value("${bunnycure.whatsapp.admin-alert.outbox.enabled:true}")
    private boolean outboxEnabled;

    @Value("${bunnycure.whatsapp.admin-alert.outbox.batch-size:20}")
    private int batchSize;

    @Value("${bunnycure.whatsapp.admin-alert.outbox.max-attempts:6}")
    private int maxAttempts;

    @Value("${bunnycure.whatsapp.admin-alert.outbox.retry-base-seconds:30}")
    private int retryBaseSeconds;

    public WhatsAppAdminAlertOutboxService(WhatsAppAdminAlertOutboxRepository outboxRepository,
                                           BookingRequestRepository bookingRequestRepository,
                                           WhatsAppService whatsAppService,
                                           AppSettingsService appSettingsService) {
        this.outboxRepository = outboxRepository;
        this.bookingRequestRepository = bookingRequestRepository;
        this.whatsAppService = whatsAppService;
        this.appSettingsService = appSettingsService;
    }

    @Async
    public void enqueueAndTryDispatch(Long bookingRequestId) {
        if (!isAlertActive()) {
            return;
        }
        if (!outboxEnabled) {
            sendDirectWithoutOutbox(bookingRequestId);
            return;
        }

        enqueue(bookingRequestId);
        dispatchDueAlerts();
    }

    @Scheduled(cron = "${bunnycure.whatsapp.admin-alert.outbox.dispatcher-cron:0 */1 * * * *}")
    public void dispatchDueAlertsOnSchedule() {
        if (!isAlertActive() || !outboxEnabled) {
            return;
        }
        dispatchDueAlerts();
    }

    @Transactional
    public void enqueue(Long bookingRequestId) {
        if (bookingRequestId == null) {
            return;
        }
        boolean exists = outboxRepository.findByBookingRequestId(bookingRequestId).isPresent();
        if (exists) {
            return;
        }

        WhatsAppAdminAlertOutbox event = WhatsAppAdminAlertOutbox.builder()
                .bookingRequestId(bookingRequestId)
                .status(OutboxStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(LocalDateTime.now())
                .build();

        try {
            outboxRepository.save(event);
        } catch (DataIntegrityViolationException ex) {
            // If another thread inserted the same booking_request_id first, treat as already enqueued.
            log.debug("[WHATSAPP-ADMIN-OUTBOX] Reserva {} ya estaba encolada", bookingRequestId);
        }
    }

    @Transactional
    public void dispatchDueAlerts() {
        int safeBatchSize = batchSize > 0 ? batchSize : 20;

        List<WhatsAppAdminAlertOutbox> due = outboxRepository
                .findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        DUE_STATUSES,
                        LocalDateTime.now(),
                        PageRequest.of(0, safeBatchSize)
                );

        for (WhatsAppAdminAlertOutbox event : due) {
            processEvent(event);
        }
    }

    private void processEvent(WhatsAppAdminAlertOutbox event) {
        BookingRequest request = bookingRequestRepository.findById(event.getBookingRequestId()).orElse(null);
        if (request == null) {
            markAsFailed(event, "Booking request no encontrada");
            return;
        }

        String targetNumber = resolveAdminAlertNumber();
        boolean sent = whatsAppService.sendAdminBookingAlertSync(targetNumber, request);
        if (sent) {
            event.setStatus(OutboxStatus.SENT);
            event.setSentAt(LocalDateTime.now());
            event.setLastError(null);
            outboxRepository.save(event);
            return;
        }

        int attempts = event.getAttemptCount() + 1;
        event.setAttemptCount(attempts);

        if (attempts >= maxAttempts) {
            markAsFailed(event, "Maximos intentos alcanzados");
            return;
        }

        event.setStatus(OutboxStatus.RETRY);
        event.setLastError("No se pudo enviar por WhatsApp API");
        event.setNextAttemptAt(LocalDateTime.now().plusSeconds(calculateBackoffSeconds(attempts)));
        outboxRepository.save(event);
    }

    private void markAsFailed(WhatsAppAdminAlertOutbox event, String reason) {
        event.setStatus(OutboxStatus.FAILED);
        event.setLastError(reason);
        outboxRepository.save(event);
    }

    private void sendDirectWithoutOutbox(Long bookingRequestId) {
        BookingRequest request = bookingRequestRepository.findById(bookingRequestId).orElse(null);
        if (request == null) {
            log.warn("[WHATSAPP-ADMIN] No se pudo enviar alerta directa, reserva {} no encontrada", bookingRequestId);
            return;
        }
        whatsAppService.sendAdminBookingAlertSync(resolveAdminAlertNumber(), request);
    }

    private boolean isAlertActive() {
        if (!adminAlertEnabled) {
            return false;
        }
        String resolved = resolveAdminAlertNumber();
        if (resolved == null || resolved.isBlank()) {
            log.warn("[WHATSAPP-ADMIN] Numero admin-alert no configurado");
            return false;
        }
        return true;
    }

    private String resolveAdminAlertNumber() {
        return appSettingsService.getAdminAlertWhatsappNumber(adminAlertNumberFallback);
    }

    private long calculateBackoffSeconds(int attempts) {
        int safeBase = retryBaseSeconds > 0 ? retryBaseSeconds : 30;
        long delay = (long) safeBase << Math.max(0, attempts - 1);
        return Math.min(delay, 3600L);
    }

}
