package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.enums.PaymentProvider;
import cl.bunnycure.domain.model.SubscriptionPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** SubscriptionPayment tiene @TenantId: las consultas derivadas quedan limitadas al salón actual. */
public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {

    List<SubscriptionPayment> findAllByOrderByPaidAtDesc();

    /** Idempotencia de webhooks: busca en todos los salones (nativa, sin filtro de tenant). */
    @Query(value = "SELECT COUNT(*) > 0 FROM subscription_payments WHERE provider = :provider AND external_payment_id = :externalId",
            nativeQuery = true)
    boolean existsByProviderAndExternalPaymentIdGlobally(@Param("provider") String provider, @Param("externalId") String externalId);

    default boolean existsGlobally(PaymentProvider provider, String externalPaymentId) {
        return existsByProviderAndExternalPaymentIdGlobally(provider.name(), externalPaymentId);
    }
}
