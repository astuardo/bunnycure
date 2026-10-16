package cl.bunnycure.service;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Servicio de infraestructura para ejecutar operaciones en background iterando sobre todos
 * los salones activos o en el contexto de un salón específico.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantExecutionService {

    private final TenantRepository tenantRepository;

    private org.springframework.transaction.support.TransactionTemplate requiresNewTransaction;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTransactionManager(org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.requiresNewTransaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Como {@link #executeInTenantContext(Long, Supplier)}, pero en una transacción NUEVA.
     *
     * Hibernate fija el salón de una sesión al abrirla: si el llamador ya tiene una transacción
     * (o una sesión abierta por Open-Session-In-View) del salón A, cambiar TenantContext a B no
     * cambia esa sesión y las entidades con @TenantId se guardarían/leerían como del salón A
     * (Hibernate lo rechaza con "assigned tenant id differs from current tenant id").
     * REQUIRES_NEW suspende la sesión del llamador y abre una propia ligada al salón B.
     * Usar siempre que se lea o escriba una entidad con @TenantId de OTRO salón.
     */
    public <T> T executeInTenantTransaction(Long tenantId, Supplier<T> supplier) {
        if (requiresNewTransaction == null) {
            return executeInTenantContext(tenantId, supplier);
        }
        return executeInTenantContext(tenantId, () -> requiresNewTransaction.execute(status -> supplier.get()));
    }

    /**
     * Ejecuta una tarea para cada salón activo registrado en la plataforma.
     * En cada iteración establece TenantContext con el ID del salón y al terminar restaura el contexto previo.
     */
    public void executeForAllActiveTenants(String taskName, Consumer<Tenant> action) {
        Long previousTenantId = TenantContext.getExplicitTenantId();
        List<Tenant> activeTenants;
        try {
            activeTenants = tenantRepository.findByActiveTrue();
        } catch (Exception ex) {
            log.error("[TENANT-EXEC] Error listando salones activos para {}: {}", taskName, ex.getMessage(), ex);
            return;
        }

        if (activeTenants == null || activeTenants.isEmpty()) {
            log.debug("[TENANT-EXEC] No hay salones activos para ejecutar {}", taskName);
            return;
        }

        for (Tenant tenant : activeTenants) {
            try {
                TenantContext.setCurrentTenantId(tenant.getId());
                log.debug("[TENANT-EXEC] Iniciando {} para salón '{}' (ID={})", taskName, tenant.getName(), tenant.getId());
                action.accept(tenant);
            } catch (Exception ex) {
                log.error("[TENANT-EXEC] Error ejecutando {} en salón '{}' (ID={}): {}",
                        taskName, tenant.getName(), tenant.getId(), ex.getMessage(), ex);
            } finally {
                TenantContext.setCurrentTenantId(previousTenantId);
            }
        }
    }

    /**
     * Ejecuta una acción puntual dentro del contexto de un tenant específico.
     * Restaura el contexto previo al terminar: si se invoca dentro de una petición HTTP,
     * el resto de la petición sigue en su salón (antes se limpiaba y caía al tenant por defecto).
     */
    public void executeInTenantContext(Long tenantId, Runnable action) {
        executeInTenantContext(tenantId, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Ejecuta una función con retorno dentro del contexto de un tenant específico,
     * restaurando el contexto previo al terminar.
     */
    public <T> T executeInTenantContext(Long tenantId, Supplier<T> supplier) {
        Long previousTenantId = TenantContext.getExplicitTenantId();
        try {
            TenantContext.setCurrentTenantId(tenantId);
            return supplier.get();
        } finally {
            TenantContext.setCurrentTenantId(previousTenantId);
        }
    }
}
