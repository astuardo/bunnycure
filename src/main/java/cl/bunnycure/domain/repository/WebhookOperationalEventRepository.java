package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.WebhookOperationalEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface WebhookOperationalEventRepository extends JpaRepository<WebhookOperationalEvent, Long> {

	@Transactional
	long deleteByCreatedAtBefore(LocalDateTime threshold);
}
