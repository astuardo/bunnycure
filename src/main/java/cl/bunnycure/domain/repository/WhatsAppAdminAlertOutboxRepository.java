package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.enums.OutboxStatus;
import cl.bunnycure.domain.model.WhatsAppAdminAlertOutbox;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WhatsAppAdminAlertOutboxRepository extends JpaRepository<WhatsAppAdminAlertOutbox, Long> {

    Optional<WhatsAppAdminAlertOutbox> findByBookingRequestId(Long bookingRequestId);

    List<WhatsAppAdminAlertOutbox> findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<OutboxStatus> statuses,
            LocalDateTime now,
            Pageable pageable
    );

    @Transactional
    long deleteByStatusInAndCreatedAtBefore(Collection<OutboxStatus> statuses, LocalDateTime threshold);
}
