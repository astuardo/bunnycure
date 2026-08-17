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
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseService {

    private final ProductRepository productRepository;
    private final InventoryMovementRepository movementRepository;

    /**
     * Register a purchase: updates purchase price, increases product stock and records movement.
     */
    @Transactional
    public InventoryMovement registerPurchase(PurchaseRequestDto req) {
        Product product = productRepository.findByIdForUpdate(req.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Producto no existe: " + req.getProductId()));

        BigDecimal addedConsumption = product.getConversionFactor().multiply(req.getPurchaseQuantity());
        BigDecimal currentStock = product.getStockConsumptionUnit() != null ? product.getStockConsumptionUnit() : BigDecimal.ZERO;

        product.setStockConsumptionUnit(currentStock.add(addedConsumption));

        BigDecimal oldPurchasePrice = product.getPurchasePrice();
        if (req.getUnitPurchasePrice() != null && req.getUnitPurchasePrice().compareTo(BigDecimal.ZERO) > 0) {
            product.setPurchasePrice(req.getUnitPurchasePrice());
        }

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

        if (oldPurchasePrice != null && req.getUnitPurchasePrice() != null && oldPurchasePrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal delta = req.getUnitPurchasePrice().subtract(oldPurchasePrice);
            BigDecimal pct = delta.multiply(BigDecimal.valueOf(100)).divide(oldPurchasePrice, 2, RoundingMode.HALF_UP);
            log.info("[Inventory-Purchase] Product {} purchase price changed: {} -> {} (delta: {}, {}%)",
                    product.getName(), oldPurchasePrice, req.getUnitPurchasePrice(), delta, pct);
        }

        log.info("[Inventory-Purchase] Registered purchase: {} {} ({} /unit) -> +{} {} to stock for product {}",
                req.getPurchaseQuantity(), product.getPurchaseUnit(), req.getUnitPurchasePrice(),
                addedConsumption, product.getConsumptionUnit(), product.getName());

        return saved;
    }
}
