package cl.bunnycure.service;

import cl.bunnycure.domain.model.Product;
import cl.bunnycure.domain.model.ServiceMaterialUsage;
import cl.bunnycure.domain.repository.ProductRepository;
import cl.bunnycure.domain.repository.ServiceMaterialUsageRepository;
import cl.bunnycure.domain.repository.InventoryMovementRepository;
import cl.bunnycure.web.dto.ConsumeRequestDto;
import cl.bunnycure.web.dto.MaterialUsageDto;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final ProductRepository productRepository;
    private final ServiceMaterialUsageRepository usageRepository;
    private final InventoryMovementRepository movementRepository;

    /**
     * Consume materials for a completed service.
     * Uses pessimistic locking to avoid race conditions when updating stock.
     * Also records an inventory movement of type CONSUMPTION for auditing.
     */
    @Transactional
    public void consumeMaterialsForService(ConsumeRequestDto request) {
        Long serviceId = request.getServiceId();
        Long usedBy = request.getUsedByUserId();

        Map<Long, Product> lockedProducts = new HashMap<>();

        List<MaterialUsageDto> usages = request.getUsages();

        for (MaterialUsageDto u : usages) {
            Product product = productRepository.findByIdForUpdate(u.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no existe: " + u.getProductId()));

            lockedProducts.put(product.getId(), product);

            BigDecimal newStock = product.getStockConsumptionUnit().subtract(u.getQuantity());
            if (newStock.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException("Stock insuficiente para producto: " + product.getName());
            }

            product.setStockConsumptionUnit(newStock);
            productRepository.save(product);

            ServiceMaterialUsage usage = ServiceMaterialUsage.builder()
                    .product(product)
                    .serviceId(serviceId)
                    .quantity(u.getQuantity())
                    .usedByUserId(usedBy)
                    .build();

            usageRepository.save(usage);

            // Record inventory movement
            movementRepository.save(
                    cl.bunnycure.domain.model.InventoryMovement.builder()
                            .product(product)
                            .movementType(cl.bunnycure.domain.model.MovementType.CONSUMPTION)
                            .quantityConsumptionUnit(u.getQuantity())
                            .createdBy(usedBy)
                            .build()
            );

            log.info("[Inventory] Consumed {} {} of product {} (service {})", u.getQuantity(), product.getConsumptionUnit(), product.getName(), serviceId);
        }
    }
}
