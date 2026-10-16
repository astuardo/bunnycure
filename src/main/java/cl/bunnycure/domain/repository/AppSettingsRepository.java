package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** AppSettings tiene @TenantId: todas las consultas quedan limitadas al salón actual. */
public interface AppSettingsRepository extends JpaRepository<AppSettings, Long> {
    Optional<AppSettings> findByKey(String key);
}
