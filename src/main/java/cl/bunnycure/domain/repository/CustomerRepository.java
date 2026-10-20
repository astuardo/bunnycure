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

    Optional<Customer> findByRut(String rut);

    List<Customer> findByFullNameContainingIgnoreCase(String name);

    boolean existsByEmail(String email);

    boolean existsByRut(String rut);

    @org.springframework.data.jpa.repository.Query(value = "SELECT c.tenant_id FROM customers c WHERE c.public_id = :publicId LIMIT 1", nativeQuery = true)
    Optional<Long> findTenantIdByPublicId(@org.springframework.data.repository.query.Param("publicId") String publicId);

    @Override
    @Query("SELECT c FROM Customer c WHERE c.id = :id")
    Optional<Customer> findById(@org.springframework.data.repository.query.Param("id") Long id);

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

    @Query(value = """
        SELECT c, COUNT(a) as appointmentCount
        FROM Customer c
        LEFT JOIN c.appointments a
        GROUP BY c
        ORDER BY c.fullName ASC
    """, countQuery = "SELECT COUNT(c) FROM Customer c")
    org.springframework.data.domain.Page<Object[]> findAllWithAppointmentCount(org.springframework.data.domain.Pageable pageable);

    @Query("""
        SELECT c, COUNT(a) as appointmentCount
        FROM Customer c
        LEFT JOIN c.appointments a
        WHERE c.searchText LIKE CONCAT('%', :t1, '%')
           AND c.searchText LIKE CONCAT('%', :t2, '%')
           AND c.searchText LIKE CONCAT('%', :t3, '%')
           AND c.searchText LIKE CONCAT('%', :t4, '%')
        GROUP BY c
        ORDER BY c.fullName ASC
    """)
    List<Object[]> searchWithAppointmentCount(@org.springframework.data.repository.query.Param("t1") String t1,
            @org.springframework.data.repository.query.Param("t2") String t2,
            @org.springframework.data.repository.query.Param("t3") String t3,
            @org.springframework.data.repository.query.Param("t4") String t4);

    @Query(value = """
        SELECT c, COUNT(a) as appointmentCount
        FROM Customer c
        LEFT JOIN c.appointments a
        WHERE c.searchText LIKE CONCAT('%', :t1, '%')
           AND c.searchText LIKE CONCAT('%', :t2, '%')
           AND c.searchText LIKE CONCAT('%', :t3, '%')
           AND c.searchText LIKE CONCAT('%', :t4, '%')
        GROUP BY c
        ORDER BY c.fullName ASC
    """, countQuery = """
        SELECT COUNT(c) FROM Customer c
        WHERE c.searchText LIKE CONCAT('%', :t1, '%')
           AND c.searchText LIKE CONCAT('%', :t2, '%')
           AND c.searchText LIKE CONCAT('%', :t3, '%')
           AND c.searchText LIKE CONCAT('%', :t4, '%')
    """)
    org.springframework.data.domain.Page<Object[]> searchWithAppointmentCount(
            @org.springframework.data.repository.query.Param("t1") String t1,
            @org.springframework.data.repository.query.Param("t2") String t2,
            @org.springframework.data.repository.query.Param("t3") String t3,
            @org.springframework.data.repository.query.Param("t4") String t4,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("""
        SELECT c, COUNT(a) as appointmentCount
        FROM Customer c
        INNER JOIN c.appointments a
        WHERE a.specialist.id = :specialistId
        GROUP BY c
        ORDER BY c.fullName ASC
    """)
    List<Object[]> findSpecialistCustomers(@org.springframework.data.repository.query.Param("specialistId") Long specialistId);

    @Query(value = """
        SELECT c, COUNT(a) as appointmentCount
        FROM Customer c
        INNER JOIN c.appointments a
        WHERE a.specialist.id = :specialistId
        GROUP BY c
        ORDER BY c.fullName ASC
    """, countQuery = """
        SELECT COUNT(DISTINCT c) FROM Customer c
        INNER JOIN c.appointments a
        WHERE a.specialist.id = :specialistId
    """)
    org.springframework.data.domain.Page<Object[]> findSpecialistCustomers(
            @org.springframework.data.repository.query.Param("specialistId") Long specialistId,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("""
        SELECT c, COUNT(a) as appointmentCount
        FROM Customer c
        INNER JOIN c.appointments a
        WHERE a.specialist.id = :specialistId
          AND (c.searchText LIKE CONCAT('%', :t1, '%')
           AND c.searchText LIKE CONCAT('%', :t2, '%')
           AND c.searchText LIKE CONCAT('%', :t3, '%')
           AND c.searchText LIKE CONCAT('%', :t4, '%'))
        GROUP BY c
        ORDER BY c.fullName ASC
    """)
    List<Object[]> searchSpecialistCustomers(
            @org.springframework.data.repository.query.Param("specialistId") Long specialistId,
            @org.springframework.data.repository.query.Param("t1") String t1,
            @org.springframework.data.repository.query.Param("t2") String t2,
            @org.springframework.data.repository.query.Param("t3") String t3,
            @org.springframework.data.repository.query.Param("t4") String t4
    );

    @Query(value = """
        SELECT c, COUNT(a) as appointmentCount
        FROM Customer c
        INNER JOIN c.appointments a
        WHERE a.specialist.id = :specialistId
          AND (c.searchText LIKE CONCAT('%', :t1, '%')
           AND c.searchText LIKE CONCAT('%', :t2, '%')
           AND c.searchText LIKE CONCAT('%', :t3, '%')
           AND c.searchText LIKE CONCAT('%', :t4, '%'))
        GROUP BY c
        ORDER BY c.fullName ASC
    """, countQuery = """
        SELECT COUNT(DISTINCT c) FROM Customer c
        INNER JOIN c.appointments a
        WHERE a.specialist.id = :specialistId
          AND (c.searchText LIKE CONCAT('%', :t1, '%')
           AND c.searchText LIKE CONCAT('%', :t2, '%')
           AND c.searchText LIKE CONCAT('%', :t3, '%')
           AND c.searchText LIKE CONCAT('%', :t4, '%'))
    """)
    org.springframework.data.domain.Page<Object[]> searchSpecialistCustomers(
            @org.springframework.data.repository.query.Param("specialistId") Long specialistId,
            @org.springframework.data.repository.query.Param("t1") String t1,
            @org.springframework.data.repository.query.Param("t2") String t2,
            @org.springframework.data.repository.query.Param("t3") String t3,
            @org.springframework.data.repository.query.Param("t4") String t4,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.customer.id = :customerId")
    long countAppointmentsByCustomerId(Long customerId);
}