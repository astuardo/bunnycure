package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.customer
        JOIN FETCH a.service
        WHERE a.appointmentDate BETWEEN :start AND :end
        ORDER BY a.appointmentDate ASC, a.appointmentTime ASC
    """)
    List<Appointment> findByDateRangeWithCustomer(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.customer
        JOIN FETCH a.service
        WHERE a.appointmentDate = :date
        ORDER BY a.appointmentTime ASC
    """)
    List<Appointment> findByDateWithCustomer(@Param("date") LocalDate date);

    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.customer
        JOIN FETCH a.service
        WHERE a.id = :id
    """)
    Optional<Appointment> findByIdWithDetails(@Param("id") Long id);

    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.customer
        JOIN FETCH a.service
        WHERE a.customer.id = :customerId
        ORDER BY a.appointmentDate DESC, a.appointmentTime DESC
    """)
    List<Appointment> findByCustomerIdOrderByAppointmentDateDescAppointmentTimeDesc(
            @Param("customerId") Long customerId
    );

    List<Appointment> findByStatusAndNotificationSentFalse(AppointmentStatus status);

    @Query("""
        SELECT COUNT(a) FROM Appointment a
        WHERE a.status = :status
        AND a.appointmentDate = :date
    """)
    long countByStatusAndAppointmentDate(
            @Param("status") AppointmentStatus status,
            @Param("date") LocalDate date
    );
}