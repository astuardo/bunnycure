package cl.bunnycure.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.DockerClientFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * En CI (GitHub Actions define CI=true) Docker es obligatorio: sin él,
 * PostgresMigrationIntegrationTest se omitiría en silencio y el deploy seguiría
 * sin haber probado las migraciones contra PostgreSQL real.
 */
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
@DisplayName("CI: Docker disponible para las pruebas de migraciones en PostgreSQL")
class CiDockerAvailabilityTest {

    @Test
    void dockerIsAvailableInCi() {
        assertThat(DockerClientFactory.instance().isDockerAvailable())
                .as("Docker no disponible en CI: las migraciones no se probarían contra PostgreSQL")
                .isTrue();
    }
}
