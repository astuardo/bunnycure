package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByCustomDomainIgnoreCase(String customDomain);

    Optional<Tenant> findBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsByCustomDomainIgnoreCase(String customDomain);
}
