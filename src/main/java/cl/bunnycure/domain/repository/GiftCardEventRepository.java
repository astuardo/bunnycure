package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.GiftCardEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GiftCardEventRepository extends JpaRepository<GiftCardEvent, Long> {
    List<GiftCardEvent> findByGiftCardIdOrderByCreatedAtDesc(Long giftCardId);
}
