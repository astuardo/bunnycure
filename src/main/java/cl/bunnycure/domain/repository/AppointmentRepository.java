package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // ✅ Vista semanal — con JOIN FETCH
    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.customer
        WHERE a.appointmentDate BETWEEN :start AND :end
        ORDER BY a.appointmentDate ASC, a.appointmentTime ASC
    """)
    List<Appointment> findByDateRangeWithCustomer(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    // ✅ Dashboard — ya tenía JOIN FETCH
    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.customer
        WHERE a.appointmentDate = :date
        ORDER BY a.appointmentTime ASC
    """)
    List<Appointment> findByDateWithCustomer(@Param("date") LocalDate date);

    // ✅ Historial por cliente — no necesita JOIN FETCH (customer ya está en contexto)
    List<Appointment> findByCustomerIdOrderByAppointmentDateDescAppointmentTimeDesc(
            Long customerId
    );

    // ✅ Notificaciones pendientes
    List<Appointment> findByStatusAndNotificationSentFalse(AppointmentStatus status);

    // ✅ Contadores para dashboard
    long countByStatusAndAppointmentDate(AppointmentStatus status, LocalDate date);
}
