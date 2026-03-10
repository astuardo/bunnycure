package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.enums.BookingRequestStatus;
import cl.bunnycure.domain.model.BookingRequest;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingRequestRepository extends JpaRepository<BookingRequest, Long> {

    // Pendientes ordenadas por fecha de solicitud (más antigua primero)
    List<BookingRequest> findByStatusOrderByCreatedAtAsc(BookingRequestStatus status);

    // Todas ordenadas por fecha descendente
    List<BookingRequest> findAllByOrderByCreatedAtDesc();

    // Conteo de pendientes (para badge en dashboard)
    long countByStatus(BookingRequestStatus status);

    // Con JOIN FETCH para evitar LazyInitializationException
    @Query("""
        SELECT r FROM BookingRequest r
        JOIN FETCH r.service
        WHERE r.status = :status
        ORDER BY r.createdAt ASC
    """)
    List<BookingRequest> findPendingWithService(BookingRequestStatus status);

    @Query("""
        SELECT r FROM BookingRequest r
        JOIN FETCH r.service
        ORDER BY r.createdAt DESC
    """)
    List<BookingRequest> findAllWithService();

    boolean existsByAppointmentId(Long appointmentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE BookingRequest r
        SET r.appointment = null
        WHERE r.appointment.id = :appointmentId
    """)
    int clearAppointmentByAppointmentId(@Param("appointmentId") Long appointmentId);
}
