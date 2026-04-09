package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import cl.bunnycure.domain.model.Customer;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("""
        SELECT COUNT(a) FROM Appointment a
        LEFT JOIN a.services svc
        WHERE a.service.id = :serviceId OR svc.id = :serviceId
    """)
    long countByServiceId(@Param("serviceId") Long serviceId);

    @Query("""
        SELECT DISTINCT a FROM Appointment a
        JOIN FETCH a.customer
        JOIN FETCH a.service
        LEFT JOIN FETCH a.services
        WHERE a.appointmentDate BETWEEN :start AND :end
        ORDER BY a.appointmentDate ASC, a.appointmentTime ASC
    """)
    List<Appointment> findByDateRangeWithCustomer(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    @Query("""
        SELECT DISTINCT a FROM Appointment a
        JOIN FETCH a.customer
        JOIN FETCH a.service
        LEFT JOIN FETCH a.services
        WHERE a.appointmentDate = :date
        ORDER BY a.appointmentTime ASC
    """)
    List<Appointment> findByDateWithCustomer(@Param("date") LocalDate date);

    @Query("""
        SELECT DISTINCT a FROM Appointment a
        JOIN FETCH a.customer
        JOIN FETCH a.service
        LEFT JOIN FETCH a.services
        WHERE a.id = :id
    """)
    Optional<Appointment> findByIdWithDetails(@Param("id") Long id);

    @Query("""
        SELECT DISTINCT a FROM Appointment a
        JOIN FETCH a.customer
        JOIN FETCH a.service
        LEFT JOIN FETCH a.services
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

    @Query("""
        SELECT DISTINCT a FROM Appointment a
        JOIN FETCH a.customer
        JOIN FETCH a.service
        LEFT JOIN FETCH a.services
        WHERE a.status = :status
        AND a.reminderSent = false
        AND a.appointmentDate = :date
        ORDER BY a.appointmentTime ASC
    """)
    List<Appointment> findPendingRemindersForDate(
            @Param("status") AppointmentStatus status,
            @Param("date") LocalDate date
    );

    @Query("""
        SELECT DISTINCT a FROM Appointment a
        JOIN FETCH a.customer
        JOIN FETCH a.service
        LEFT JOIN FETCH a.services
        WHERE a.status IN :statuses
        AND a.reminderSent = false
        AND a.appointmentDate = :date
        ORDER BY a.appointmentTime ASC
    """)
    List<Appointment> findPendingRemindersForDateByStatuses(
            @Param("statuses") Collection<AppointmentStatus> statuses,
            @Param("date") LocalDate date
    );

    @Query("""
        SELECT DISTINCT a FROM Appointment a
        JOIN FETCH a.customer
        JOIN FETCH a.service
        LEFT JOIN FETCH a.services
        WHERE a.status IN :statuses
        AND a.reminderSent = false
        AND a.appointmentDate >= :date
        ORDER BY a.appointmentDate ASC, a.appointmentTime ASC
    """)
    List<Appointment> findPendingRemindersFromDateByStatuses(
            @Param("statuses") Collection<AppointmentStatus> statuses,
            @Param("date") LocalDate date
    );

    @Query("""
        SELECT DISTINCT a FROM Appointment a
        JOIN FETCH a.customer
        JOIN FETCH a.service
        LEFT JOIN FETCH a.services
        WHERE a.status IN :statuses
        AND a.reminderSent = false
        AND a.appointmentDate = :date
        AND a.appointmentTime >= :startTime
        AND a.appointmentTime <= :endTime
        ORDER BY a.appointmentTime ASC
    """)
    List<Appointment> findPendingRemindersForDateAndTimeWindowByStatuses(
            @Param("statuses") Collection<AppointmentStatus> statuses,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    @Query("""
        SELECT COUNT(a) FROM Appointment a
        WHERE a.status IN :statuses
        AND a.reminderSent = true
        AND a.appointmentDate = :date
    """)
    long countSentRemindersForDateByStatuses(
            @Param("statuses") Collection<AppointmentStatus> statuses,
            @Param("date") LocalDate date
    );

    @Query("""
        SELECT DISTINCT a FROM Appointment a
        JOIN FETCH a.customer
        JOIN FETCH a.service
        LEFT JOIN FETCH a.services
        WHERE a.status = :status
        ORDER BY a.appointmentDate ASC, a.appointmentTime ASC
    """)
    List<Appointment> findByStatus(@Param("status") AppointmentStatus status);
}
