package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByPublicId(String publicId);

    // Nuevo: búsqueda por teléfono para matching desde solicitudes de reserva
    Optional<Customer> findByPhone(String phone);

    List<Customer> findByFullNameContainingIgnoreCase(String name);

    boolean existsByEmail(String email);

    @Query("SELECT c FROM Customer c LEFT JOIN FETCH c.appointments WHERE c.id = :id")
    Optional<Customer> findByIdWithAppointments(Long id);

    @Query("SELECT c FROM Customer c LEFT JOIN FETCH c.appointments WHERE c.publicId = :publicId")
    Optional<Customer> findByPublicIdWithAppointments(String publicId);

    @Query("""
        SELECT c, COUNT(a) as appointmentCount
        FROM Customer c
        LEFT JOIN c.appointments a
        GROUP BY c
        ORDER BY c.fullName ASC
    """)
    List<Object[]> findAllWithAppointmentCount();

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.customer.id = :customerId")
    long countAppointmentsByCustomerId(Long customerId);
}