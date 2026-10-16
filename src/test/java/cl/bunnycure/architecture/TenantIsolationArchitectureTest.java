package cl.bunnycure.architecture;

import jakarta.persistence.Entity;
import org.hibernate.annotations.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regla de arquitectura multi-tenant: toda entidad JPA debe estar particionada por salón
 * con @TenantId, salvo excepciones explícitas y justificadas en {@link #GLOBAL_ENTITIES}.
 *
 * Si este test falla al crear una entidad nueva, la respuesta por defecto es añadirle
 * un campo tenant_id con @org.hibernate.annotations.TenantId (y su migración), no
 * agregarla a la lista de excepciones.
 */
@DisplayName("Arquitectura: toda entidad JPA está aislada por salón")
class TenantIsolationArchitectureTest {

    private static final String MODEL_PACKAGE = "cl.bunnycure.domain.model";

    /** Entidades globales a propósito, con el motivo. */
    private static final Map<String, String> GLOBAL_ENTITIES = Map.of(
            "Tenant", "Es la propia tabla de salones",
            "PasswordResetToken", "Se busca por token aleatorio sin sesión; ligado a un User que sí tiene tenant",
            "EmailVerificationToken", "Se busca por hash del token sin sesión (registro autoservicio); guarda tenant_id explícito",
            "WebhookProcessedEvent", "Deduplicación global de eventos de Meta (ids únicos a nivel plataforma)",
            "WebhookOperationalEvent", "Registro operativo interno de la plataforma; no se expone por API",
            "WhatsAppAdminAlertOutbox", "Cola global: el dispatcher resuelve el salón de cada fila desde su BookingRequest",
            "GiftCardItem", "Hijo de GiftCard (con @TenantId); sin repositorio propio, solo se accede vía el padre",
            "GiftCardEvent", "Hijo de GiftCard (con @TenantId); solo se consulta por giftCardId ya aislado",
            "LoyaltyRewardHistory", "Hijo de Customer (con @TenantId); solo se consulta por customerId ya aislado",
            "ServiceMaterialUsage", "Hijo de ServiceCatalog (con @TenantId); solo se consulta por serviceId ya aislado"
    );

    @Test
    @DisplayName("Cada @Entity tiene @TenantId o una excepción justificada")
    void everyEntityIsTenantScopedOrExplicitlyGlobal() {
        Set<String> unscoped = entityClasses().stream()
                .filter(type -> !hasTenantId(type))
                .map(Class::getSimpleName)
                .filter(name -> !GLOBAL_ENTITIES.containsKey(name))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(unscoped)
                .as("Entidades sin @TenantId y sin excepción justificada (filtrarían datos entre salones)")
                .isEmpty();
    }

    @Test
    @DisplayName("La lista de excepciones no tiene entradas obsoletas")
    void globalAllowlistHasNoStaleEntries() {
        Map<String, Class<?>> entitiesByName = entityClasses().stream()
                .collect(Collectors.toMap(Class::getSimpleName, type -> type));

        Set<String> stale = GLOBAL_ENTITIES.keySet().stream()
                .filter(name -> !entitiesByName.containsKey(name) || hasTenantId(entitiesByName.get(name)))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(stale)
                .as("Excepciones que ya no existen o que ya tienen @TenantId: elimínalas de GLOBAL_ENTITIES")
                .isEmpty();
    }

    private static Set<Class<?>> entityClasses() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Set<Class<?>> classes = scanner.findCandidateComponents(MODEL_PACKAGE).stream()
                .map(BeanDefinition::getBeanClassName)
                .map(TenantIsolationArchitectureTest::load)
                .collect(Collectors.toSet());
        assertThat(classes).as("No se encontraron entidades en " + MODEL_PACKAGE).isNotEmpty();
        return classes;
    }

    private static boolean hasTenantId(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            Field[] fields = c.getDeclaredFields();
            if (Arrays.stream(fields).anyMatch(f -> f.isAnnotationPresent(TenantId.class))) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
