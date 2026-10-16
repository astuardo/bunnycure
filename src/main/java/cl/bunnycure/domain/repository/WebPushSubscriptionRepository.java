package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.WebPushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * WebPushSubscription está particionada con @TenantId: todas las consultas derivadas
 * quedan limitadas automáticamente al salón del TenantContext actual.
 */
public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, Long> {
    Optional<WebPushSubscription> findByEndpoint(String endpoint);
    List<WebPushSubscription> findByActiveTrue();
    List<WebPushSubscription> findByActiveTrueAndUserId(Long userId);

    /**
     * El endpoint es único a nivel global. Si el dispositivo estaba registrado en otro salón
     * (consulta nativa: no aplica el filtro de tenant), se elimina antes de re-registrarlo.
     */
    @Modifying
    @Query(value = "DELETE FROM web_push_subscriptions WHERE endpoint = :endpoint AND tenant_id <> :tenantId", nativeQuery = true)
    int deleteByEndpointOutsideTenant(@Param("endpoint") String endpoint, @Param("tenantId") Long tenantId);
}
