package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.WebhookProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface WebhookProcessedEventRepository extends JpaRepository<WebhookProcessedEvent, String> {

    @Transactional
    long deleteByExpiresAtBefore(LocalDateTime threshold);
}
