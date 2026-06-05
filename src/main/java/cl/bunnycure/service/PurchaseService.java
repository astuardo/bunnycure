package cl.bunnycure.service;

import cl.bunnycure.domain.model.InventoryMovement;
import cl.bunnycure.domain.model.MovementType;
import cl.bunnycure.domain.model.Product;
import cl.bunnycure.domain.repository.InventoryMovementRepository;
import cl.bunnycure.domain.repository.ProductRepository;
import cl.bunnycure.web.dto.PurchaseRequestDto;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseService {

    private final ProductRepository productRepository;
    private final InventoryMovementRepository movementRepository;

    /**
     * Register a purchase: increases product stock (in consumption unit) and records movement.
     */
    @Transactional
    public InventoryMovement registerPurchase(PurchaseRequestDto req) {
        Product product = productRepository.findByIdForUpdate(req.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Producto no existe: " + req.getProductId()));

        BigDecimal addedConsumption = product.getConversionFactor().multiply(req.getPurchaseQuantity());

        product.setStockConsumptionUnit(product.getStockConsumptionUnit().add(addedConsumption));
        productRepository.save(product);

        InventoryMovement movement = InventoryMovement.builder()
                .product(product)
                .movementType(MovementType.PURCHASE)
                .quantityConsumptionUnit(addedConsumption)
                .quantityPurchaseUnit(req.getPurchaseQuantity())
                .unitPurchasePrice(req.getUnitPurchasePrice())
                .reference(req.getReference())
                .createdBy(req.getCreatedBy())
                .build();

        InventoryMovement saved = movementRepository.save(movement);

        log.info("[Inventory] Registered purchase: {} {} ({} {}) -> +{} {} to stock for product {}",
                req.getPurchaseQuantity(), product.getPurchaseUnit(), req.getUnitPurchasePrice(), "/unit",
                addedConsumption, product.getConsumptionUnit(), product.getName());

        return saved;
    }
}
