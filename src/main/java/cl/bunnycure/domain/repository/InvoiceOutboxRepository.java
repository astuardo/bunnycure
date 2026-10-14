package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.enums.InvoiceOutboxStatus;
import cl.bunnycure.domain.model.InvoiceOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceOutboxRepository extends JpaRepository<InvoiceOutbox, Long> {

    Optional<InvoiceOutbox> findByAppointmentId(Long appointmentId);

    @Query("SELECT o FROM InvoiceOutbox o WHERE o.status IN (:statuses) AND o.nextAttemptAt <= :now ORDER BY o.nextAttemptAt ASC")
    List<InvoiceOutbox> findPendingToProcess(
            @Param("statuses") List<InvoiceOutboxStatus> statuses,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    List<InvoiceOutbox> findByTenantIdAndStatus(Long tenantId, InvoiceOutboxStatus status);

    long countByTenantIdAndStatus(Long tenantId, InvoiceOutboxStatus status);
}
