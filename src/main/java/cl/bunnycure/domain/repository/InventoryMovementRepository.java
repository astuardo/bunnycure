package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    @Override
    @org.springframework.data.jpa.repository.Query("SELECT im FROM InventoryMovement im WHERE im.id = :id")
    java.util.Optional<InventoryMovement> findById(@org.springframework.data.repository.query.Param("id") Long id);

    List<InventoryMovement> findByProductIdOrderByCreatedAtDesc(Long productId);
}
