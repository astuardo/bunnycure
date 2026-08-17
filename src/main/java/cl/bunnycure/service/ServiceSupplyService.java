package cl.bunnycure.service;

import cl.bunnycure.domain.model.Product;
import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.domain.model.ServiceSupply;
import cl.bunnycure.domain.repository.ProductRepository;
import cl.bunnycure.domain.repository.ServiceCatalogRepository;
import cl.bunnycure.domain.repository.ServiceSupplyRepository;
import cl.bunnycure.web.dto.ServiceCostSummaryDto;
import cl.bunnycure.web.dto.ServiceSupplyDto;
import cl.bunnycure.web.dto.ServiceSupplyResponseDto;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceSupplyService {

    private final ServiceSupplyRepository serviceSupplyRepository;
    private final ServiceCatalogRepository serviceCatalogRepository;
    private final ProductRepository productRepository;

    public List<ServiceSupplyResponseDto> getSuppliesForService(Long serviceId) {
        List<ServiceSupply> supplies = serviceSupplyRepository.findByServiceIdWithProduct(serviceId);
        return supplies.stream().map(this::toResponseDto).toList();
    }

    @Transactional
    public List<ServiceSupplyResponseDto> saveSuppliesForService(Long serviceId, List<ServiceSupplyDto> supplyDtos) {
        ServiceCatalog service = serviceCatalogRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Servicio no existe: " + serviceId));

        serviceSupplyRepository.deleteByServiceId(serviceId);

        if (supplyDtos == null || supplyDtos.isEmpty()) {
            return List.of();
        }

        List<ServiceSupply> toSave = new ArrayList<>();
        for (ServiceSupplyDto dto : supplyDtos) {
            Product product = productRepository.findById(dto.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no existe: " + dto.getProductId()));

            ServiceSupply supply = ServiceSupply.builder()
                    .service(service)
                    .product(product)
                    .quantityConsumptionUnit(dto.getQuantityConsumptionUnit())
                    .build();
            toSave.add(supply);
        }

        List<ServiceSupply> saved = serviceSupplyRepository.saveAll(toSave);
        log.info("[Inventory-Supplies] Saved {} supplies for service {}", saved.size(), service.getName());

        return saved.stream().map(this::toResponseDto).toList();
    }

    public ServiceCostSummaryDto getCostSummaryForService(Long serviceId) {
        ServiceCatalog service = serviceCatalogRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Servicio no existe: " + serviceId));

        List<ServiceSupplyResponseDto> supplies = getSuppliesForService(serviceId);
        BigDecimal totalCost = supplies.stream()
                .map(ServiceSupplyResponseDto::getTotalEstimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal price = service.getPrice() != null ? service.getPrice() : BigDecimal.ZERO;
        BigDecimal grossMargin = price.subtract(totalCost);
        BigDecimal marginPct = BigDecimal.ZERO;
        if (price.compareTo(BigDecimal.ZERO) > 0) {
            marginPct = grossMargin.multiply(BigDecimal.valueOf(100)).divide(price, 2, RoundingMode.HALF_UP);
        }

        return ServiceCostSummaryDto.builder()
                .serviceId(service.getId())
                .serviceName(service.getName())
                .servicePrice(price)
                .totalMaterialsCost(totalCost)
                .grossMargin(grossMargin)
                .grossMarginPercentage(marginPct)
                .supplies(supplies)
                .build();
    }

    public List<ServiceCostSummaryDto> getAllServicesCostSummary() {
        List<ServiceCatalog> allServices = serviceCatalogRepository.findAll();
        return allServices.stream()
                .map(s -> getCostSummaryForService(s.getId()))
                .toList();
    }

    public ServiceSupplyResponseDto toResponseDto(ServiceSupply supply) {
        Product p = supply.getProduct();
        BigDecimal convFactor = p.getConversionFactor() != null && p.getConversionFactor().compareTo(BigDecimal.ZERO) > 0
                ? p.getConversionFactor()
                : BigDecimal.ONE;

        BigDecimal purchasePrice = p.getPurchasePrice() != null ? p.getPurchasePrice() : BigDecimal.ZERO;
        BigDecimal unitConsumptionCost = purchasePrice.divide(convFactor, 4, RoundingMode.HALF_UP);
        BigDecimal totalCost = unitConsumptionCost.multiply(supply.getQuantityConsumptionUnit()).setScale(2, RoundingMode.HALF_UP);

        return ServiceSupplyResponseDto.builder()
                .id(supply.getId())
                .serviceId(supply.getService().getId())
                .productId(p.getId())
                .productName(p.getName())
                .purchaseUnit(p.getPurchaseUnit())
                .consumptionUnit(p.getConsumptionUnit())
                .conversionFactor(convFactor)
                .quantityConsumptionUnit(supply.getQuantityConsumptionUnit())
                .productPurchasePrice(purchasePrice)
                .unitConsumptionCost(unitConsumptionCost)
                .totalEstimatedCost(totalCost)
                .currentStock(p.getStockConsumptionUnit() != null ? p.getStockConsumptionUnit() : BigDecimal.ZERO)
                .build();
    }
}
