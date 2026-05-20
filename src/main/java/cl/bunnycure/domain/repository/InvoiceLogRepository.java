package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.InvoiceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface InvoiceLogRepository extends JpaRepository<InvoiceLog, Long> {
    Optional<InvoiceLog> findByAppointmentId(Long appointmentId);
    Optional<InvoiceLog> findByInvoiceNumber(String invoiceNumber);
    long countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String status, LocalDateTime startInclusive, LocalDateTime endExclusive);
}
