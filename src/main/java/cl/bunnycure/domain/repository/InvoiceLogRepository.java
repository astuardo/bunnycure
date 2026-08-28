package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.InvoiceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceLogRepository extends JpaRepository<InvoiceLog, Long> {
    Optional<InvoiceLog> findByAppointmentId(Long appointmentId);
    Optional<InvoiceLog> findByInvoiceNumber(String invoiceNumber);
    Optional<InvoiceLog> findBySiiCode(String siiCode);
    long countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String status, LocalDateTime startInclusive, LocalDateTime endExclusive);

    long countByStatus(String status);

    @Query("""
        SELECT COALESCE(SUM(il.amountInClp), 0) FROM InvoiceLog il
        WHERE il.status = 'SUCCESS'
        AND il.createdAt >= :startInclusive
        AND il.createdAt < :endExclusive
    """)
    BigDecimal sumAmountByStatusSuccessAndCreatedAtBetween(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);

    @Query("""
        SELECT DISTINCT il FROM InvoiceLog il
        JOIN FETCH il.customer c
        JOIN FETCH il.appointment a
        WHERE il.status = :status
        AND il.createdAt >= :startInclusive
        AND il.createdAt < :endExclusive
        ORDER BY il.createdAt DESC
    """)
    List<InvoiceLog> findByStatusAndCreatedAtBetweenWithDetails(
            @Param("status") String status,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);

    @Query("""
        SELECT DISTINCT il FROM InvoiceLog il
        JOIN FETCH il.customer c
        JOIN FETCH il.appointment a
        WHERE il.status = :status
        ORDER BY il.createdAt DESC
    """)
    List<InvoiceLog> findByStatusWithDetails(@Param("status") String status);

    @Query("""
        SELECT DISTINCT il FROM InvoiceLog il
        JOIN FETCH il.customer c
        JOIN FETCH il.appointment a
        WHERE il.createdAt >= :startInclusive
        AND il.createdAt < :endExclusive
        ORDER BY il.createdAt DESC
    """)
    List<InvoiceLog> findByCreatedAtBetweenWithDetails(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);
}

