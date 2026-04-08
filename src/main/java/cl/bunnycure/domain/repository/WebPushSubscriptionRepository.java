package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.WebPushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, Long> {
    Optional<WebPushSubscription> findByEndpoint(String endpoint);
    List<WebPushSubscription> findByActiveTrue();
}
