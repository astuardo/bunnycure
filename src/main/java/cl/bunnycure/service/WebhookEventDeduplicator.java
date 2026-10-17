package cl.bunnycure.service;

import cl.bunnycure.domain.model.WebhookProcessedEvent;
import cl.bunnycure.domain.repository.WebhookProcessedEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deduplicación de eventos del webhook de WhatsApp (Meta reintenta entregas): caché en memoria
 * por instancia más registro persistido con vencimiento, compartido entre instancias.
 * Extraído de WhatsAppWebhookService.
 */
@Slf4j
class WebhookEventDeduplicator {

    private static final long DEDUPE_TTL_MILLIS = 10 * 60 * 1000L;
    private static final long DEDUPE_CLEANUP_EVERY_EVENTS = 250;

    private final Map<String, Long> processedEventIds = new ConcurrentHashMap<>();
    private final AtomicLong dedupeChecksCounter = new AtomicLong(0);
    private final WebhookProcessedEventRepository webhookProcessedEventRepository;

    WebhookEventDeduplicator(WebhookProcessedEventRepository webhookProcessedEventRepository) {
        this.webhookProcessedEventRepository = webhookProcessedEventRepository;
    }

    /** true si el evento ya se procesó (en esta instancia o, persistido, en cualquier otra). */
    boolean isDuplicate(String eventId) {
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
}
