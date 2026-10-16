package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Override
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findById(@org.springframework.data.repository.query.Param("id") Long id);
    
    Optional<User> findByUsername(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUsernameAndTenantId(String username, Long tenantId);

    Optional<User> findByEmailIgnoreCaseAndTenantId(String email, Long tenantId);
    
    boolean existsByUsername(String username);

    @org.springframework.data.jpa.repository.Query(value = "SELECT u.tenant_id FROM users u WHERE u.username = :username LIMIT 1", nativeQuery = true)
    Optional<Long> findTenantIdByUsername(@org.springframework.data.repository.query.Param("username") String username);

    /**
     * Unicidad global para el registro autoservicio: el login sin ?salon resuelve el salón
     * por username en toda la plataforma (findTenantIdByUsername), así que no debe repetirse.
     */
    @org.springframework.data.jpa.repository.Query(value = "SELECT COUNT(*) > 0 FROM users u WHERE LOWER(u.username) = LOWER(:username) OR LOWER(u.email) = LOWER(:username)", nativeQuery = true)
    boolean existsByUsernameOrEmailGlobally(@org.springframework.data.repository.query.Param("username") String username);

    /** Puestos ocupados por plan: especialistas y recepcionistas habilitadas del salón actual. */
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(u) FROM User u WHERE u.enabled = true AND UPPER(u.role) IN ('SPECIALIST', 'STAFF', 'RECEPTIONIST')")
    long countEnabledStaffSeats();
}
