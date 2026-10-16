package cl.bunnycure.db;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.config.TestMailConfiguration;
import cl.bunnycure.service.AppSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ejecuta las migraciones REALES de producción (db/migration) sobre PostgreSQL 17 —la misma
 * versión de Heroku— con validate-on-migrate activo y Hibernate en modo validate.
 *
 * El resto de la suite usa H2 con un juego de migraciones paralelo (db/migration-h2), que
 * puede divergir del SQL de producción. Este test es la red de seguridad antes de cada deploy.
 * Requiere Docker: se omite en local sin Docker y se ejecuta siempre en GitHub Actions.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestMailConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Migraciones Flyway de producción sobre PostgreSQL real")
class PostgresMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.validate-on-migrate", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppSettingsService appSettingsService;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM app_settings WHERE setting_key = 'test.pg.salon-name'");
        appSettingsService.clearCache();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Todas las migraciones se aplican sin errores y el esquema valida contra las entidades")
    void allMigrationsApplySuccessfully() {
        Integer failed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE", Integer.class);
        String latest = jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1",
                String.class);

        assertThat(failed).isZero();
        assertThat(Integer.parseInt(latest)).isGreaterThanOrEqualTo(70);
    }

    @Test
    @DisplayName("app_settings en Postgres: clave por salón y UPDATE de un salón no pisa al otro")
    void appSettingsArePartitionedOnPostgres() {
        String key = "test.pg.salon-name";
        inTenant(1L, () -> { appSettingsService.set(key, "Bunny Cure"); return null; });
        inTenant(2L, () -> { appSettingsService.set(key, "Glamour"); return null; });
        inTenant(2L, () -> { appSettingsService.set(key, "Glamour Studio"); return null; });
        appSettingsService.clearCache();

        assertThat(inTenant(1L, () -> appSettingsService.get(key, "-"))).isEqualTo("Bunny Cure");
        assertThat(inTenant(2L, () -> appSettingsService.get(key, "-"))).isEqualTo("Glamour Studio");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_settings WHERE setting_key = ?", Integer.class, key)).isEqualTo(2);
    }

    @Test
    @DisplayName("tenants.id: un salón nuevo recibe un id libre (V58 insertó id=1 sin avanzar la identidad)")
    void creatingTenantDoesNotCollideWithSeededTenant() {
        Long newId = jdbcTemplate.queryForObject(
                "INSERT INTO tenants (name, slug, active, plan_tier) VALUES ('Salón Nuevo', 'salon-nuevo-pg', TRUE, 'STANDARD') RETURNING id",
                Long.class);
        try {
            assertThat(newId).isGreaterThan(1L);
        } finally {
            jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", newId);
        }
    }

    private <T> T inTenant(Long tenantId, Supplier<T> action) {
        Long previous = TenantContext.getExplicitTenantId();
        try {
            TenantContext.setCurrentTenantId(tenantId);
            return action.get();
        } finally {
            TenantContext.setCurrentTenantId(previous);
        }
    }
}
