package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.LoyaltyReward;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LoyaltyRewardRepository extends JpaRepository<LoyaltyReward, Long> {
    List<LoyaltyReward> findAllByOrderByOrderIndexAsc();
}
