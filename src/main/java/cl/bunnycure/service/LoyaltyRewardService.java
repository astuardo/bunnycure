package cl.bunnycure.service;

import cl.bunnycure.domain.model.LoyaltyReward;
import cl.bunnycure.domain.repository.LoyaltyRewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoyaltyRewardService {

    private final LoyaltyRewardRepository repository;

    public List<LoyaltyReward> findAll() {
        return repository.findAllByOrderByOrderIndexAsc();
    }

    @Transactional
    public LoyaltyReward save(LoyaltyReward reward) {
        if (reward.getOrderIndex() == null) {
            reward.setOrderIndex(repository.findAll().size());
        }
        return repository.save(reward);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public void updateOrder(List<Long> ids) {
        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            int finalI = i;
            repository.findById(id).ifPresent(reward -> {
                reward.setOrderIndex(finalI);
                repository.save(reward);
            });
        }
    }
    
    public LoyaltyReward getRewardAt(int index) {
        List<LoyaltyReward> rewards = findAll();
        if (rewards.isEmpty()) return null;
        return rewards.get(index % rewards.size());
    }
}
