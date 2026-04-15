package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.LoyaltyRewardHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LoyaltyRewardHistoryRepository extends JpaRepository<LoyaltyRewardHistory, Long> {
    List<LoyaltyRewardHistory> findByCustomerIdOrderByEarnedAtDesc(Long customerId);
}
