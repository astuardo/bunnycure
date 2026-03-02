package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingsRepository extends JpaRepository<AppSettings, String> {
}
