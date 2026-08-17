package cl.bunnycure.service;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.*;
import cl.bunnycure.domain.repository.*;
import cl.bunnycure.web.dto.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final ProductRepository productRepository;
    private final ServiceMaterialUsageRepository usageRepository;
    private final InventoryMovementRepository movementRepository;
    private final ServiceSupplyRepository serviceSupplyRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppSettingsService appSettingsService;
    private final AppointmentService appointmentService;

    /**
     * Consume materials for a service.
     * Allows stock to go negative if insufficient, logging the deficit.
     */
    @Transactional
    public void consumeMaterialsForService(ConsumeRequestDto request) {
        Long serviceId = request.getServiceId();
        Long usedBy = request.getUsedByUserId();

        List<MaterialUsageDto> usages = request.getUsages();
        if (usages == null || usages.isEmpty()) return;

        for (MaterialUsageDto u : usages) {
            Product product = productRepository.findByIdForUpdate(u.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no existe: " + u.getProductId()));

            BigDecimal currentStock = product.getStockConsumptionUnit() != null ? product.getStockConsumptionUnit() : BigDecimal.ZERO;
            BigDecimal newStock = currentStock.subtract(u.getQuantity());

            if (newStock.compareTo(BigDecimal.ZERO) < 0) {
                log.warn("[Inventory] Stock went NEGATIVE for product {}: {} -> {} (deficit: {})",
                        product.getName(), currentStock, newStock, newStock.abs());
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

            movementRepository.save(
                    InventoryMovement.builder()
                            .product(product)
                            .movementType(MovementType.CONSUMPTION)
                            .quantityConsumptionUnit(u.getQuantity())
                            .reference("Consumo de servicio #" + serviceId)
                            .createdBy(usedBy)
                            .build()
            );

            log.info("[Inventory] Consumed {} {} of product {} (service {})", u.getQuantity(), product.getConsumptionUnit(), product.getName(), serviceId);
        }
    }

    /**
     * Get pre-calculated supplies preview for an appointment.
     */
    @Transactional(readOnly = true)
    public AppointmentSuppliesPreviewDto getSuppliesPreviewForAppointment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada: " + appointmentId));

        boolean autoEnabled = appSettingsService.getBoolean("inventory.auto_consumption.enabled", true);

        List<Long> serviceIds = new ArrayList<>();
        List<String> serviceNames = new ArrayList<>();

        if (appointment.getServices() != null && !appointment.getServices().isEmpty()) {
            for (ServiceCatalog s : appointment.getServices()) {
                serviceIds.add(s.getId());
                serviceNames.add(s.getName());
            }
        } else if (appointment.getService() != null) {
            serviceIds.add(appointment.getService().getId());
            serviceNames.add(appointment.getService().getName());
        }

        Map<Long, AppointmentSuppliesPreviewDto.AppointmentSupplyItemDto> aggregated = new LinkedHashMap<>();

        if (!serviceIds.isEmpty()) {
            List<ServiceSupply> supplies = serviceSupplyRepository.findByServiceIdsWithProduct(serviceIds);
            for (ServiceSupply ss : supplies) {
                Product p = ss.getProduct();
                Long pId = p.getId();

                BigDecimal conv = p.getConversionFactor() != null && p.getConversionFactor().compareTo(BigDecimal.ZERO) > 0
                        ? p.getConversionFactor()
                        : BigDecimal.ONE;
                BigDecimal purchasePrice = p.getPurchasePrice() != null ? p.getPurchasePrice() : BigDecimal.ZERO;
                BigDecimal unitCost = purchasePrice.divide(conv, 4, RoundingMode.HALF_UP);

                if (aggregated.containsKey(pId)) {
                    AppointmentSuppliesPreviewDto.AppointmentSupplyItemDto item = aggregated.get(pId);
                    BigDecimal newQty = item.getSuggestedQuantity().add(ss.getQuantityConsumptionUnit());
                    item.setSuggestedQuantity(newQty);
                    item.setProjectedStockAfter(item.getCurrentStock().subtract(newQty));
                    item.setEstimatedCost(unitCost.multiply(newQty).setScale(2, RoundingMode.HALF_UP));
                } else {
                    BigDecimal stock = p.getStockConsumptionUnit() != null ? p.getStockConsumptionUnit() : BigDecimal.ZERO;
                    BigDecimal suggested = ss.getQuantityConsumptionUnit();
                    BigDecimal projected = stock.subtract(suggested);
                    BigDecimal totalCost = unitCost.multiply(suggested).setScale(2, RoundingMode.HALF_UP);

                    aggregated.put(pId, AppointmentSuppliesPreviewDto.AppointmentSupplyItemDto.builder()
                            .productId(pId)
                            .productName(p.getName())
                            .consumptionUnit(p.getConsumptionUnit())
                            .suggestedQuantity(suggested)
                            .currentStock(stock)
                            .projectedStockAfter(projected)
                            .unitConsumptionCost(unitCost)
                            .estimatedCost(totalCost)
                            .build());
                }
            }
        }

        String aptDate = appointment.getAppointmentDate() != null ? appointment.getAppointmentDate().toString() : "";
        String aptTime = appointment.getAppointmentTime() != null ? appointment.getAppointmentTime().toString() : "";

        return AppointmentSuppliesPreviewDto.builder()
                .appointmentId(appointment.getId())
                .customerId(appointment.getCustomer() != null ? appointment.getCustomer().getId() : null)
                .customerName(appointment.getCustomer() != null ? appointment.getCustomer().getFullName() : "Cliente")
                .appointmentDate(aptDate)
                .appointmentTime(aptTime)
                .serviceNames(serviceNames)
                .autoConsumptionEnabled(autoEnabled)
                .supplies(new ArrayList<>(aggregated.values()))
                .build();
    }

    /**
     * Complete appointment and deduct confirmed/adjusted supplies.
     */
    @Transactional
    public void completeAppointmentWithSupplies(CompleteAppointmentWithSuppliesDto dto, Long userId) {
        Long appointmentId = dto.getAppointmentId();
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada: " + appointmentId));

        boolean autoEnabled = appSettingsService.getBoolean("inventory.auto_consumption.enabled", true);

        if (autoEnabled && Boolean.TRUE.equals(dto.getDeductSupplies()) && dto.getSupplies() != null) {
            for (CompleteAppointmentWithSuppliesDto.SuppliesUsageDto u : dto.getSupplies()) {
                if (u.getQuantity() == null || u.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;

                Product product = productRepository.findByIdForUpdate(u.getProductId())
                        .orElseThrow(() -> new IllegalArgumentException("Producto no existe: " + u.getProductId()));

                BigDecimal currentStock = product.getStockConsumptionUnit() != null ? product.getStockConsumptionUnit() : BigDecimal.ZERO;
                BigDecimal newStock = currentStock.subtract(u.getQuantity());

                product.setStockConsumptionUnit(newStock);
                productRepository.save(product);

                ServiceMaterialUsage usage = ServiceMaterialUsage.builder()
                        .product(product)
                        .serviceId(appointment.getService() != null ? appointment.getService().getId() : 0L)
                        .quantity(u.getQuantity())
                        .usedByUserId(userId)
                        .build();
                usageRepository.save(usage);

                movementRepository.save(
                        InventoryMovement.builder()
                            .product(product)
                            .movementType(MovementType.CONSUMPTION)
                            .quantityConsumptionUnit(u.getQuantity())
                            .reference("Cita #" + appointmentId + " - " + (appointment.getCustomer() != null ? appointment.getCustomer().getFullName() : "Cliente"))
                            .createdBy(userId)
                            .build()
                );

                log.info("[Inventory] Deducted {} {} for appointment {}", u.getQuantity(), product.getConsumptionUnit(), appointmentId);
            }
        }

        boolean genInvoice = dto.getGenerateInvoice() != null ? dto.getGenerateInvoice() : true;
        appointmentService.updateStatus(appointmentId, AppointmentStatus.COMPLETED, genInvoice);
    }

    /**
     * Stock Projections for the next 7 days based on scheduled appointments.
     */
    @Transactional(readOnly = true)
    public List<StockProjectionDto> getStockProjections7Days() {
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAhead = today.plusDays(7);

        List<Appointment> upcomingAppointments = appointmentRepository.findByDateRangeWithCustomer(today, sevenDaysAhead).stream()
                .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED || a.getStatus() == AppointmentStatus.PENDING)
                .toList();

        Map<Long, Integer> appointmentsPerProduct = new HashMap<>();
        Map<Long, BigDecimal> demand7DaysPerProduct = new HashMap<>();

        for (Appointment apt : upcomingAppointments) {
            List<Long> serviceIds = new ArrayList<>();
            if (apt.getServices() != null && !apt.getServices().isEmpty()) {
                apt.getServices().forEach(s -> serviceIds.add(s.getId()));
            } else if (apt.getService() != null) {
                serviceIds.add(apt.getService().getId());
            }

            if (!serviceIds.isEmpty()) {
                List<ServiceSupply> supplies = serviceSupplyRepository.findByServiceIdsWithProduct(serviceIds);
                for (ServiceSupply ss : supplies) {
                    Long pId = ss.getProduct().getId();
                    demand7DaysPerProduct.merge(pId, ss.getQuantityConsumptionUnit(), BigDecimal::add);
                    appointmentsPerProduct.merge(pId, 1, Integer::sum);
                }
            }
        }

        List<Product> allProducts = productRepository.findAll();
        List<StockProjectionDto> result = new ArrayList<>();

        for (Product p : allProducts) {
            BigDecimal currentStock = p.getStockConsumptionUnit() != null ? p.getStockConsumptionUnit() : BigDecimal.ZERO;
            BigDecimal demand = demand7DaysPerProduct.getOrDefault(p.getId(), BigDecimal.ZERO);
            BigDecimal balanceAfter = currentStock.subtract(demand);
            Integer aptCount = appointmentsPerProduct.getOrDefault(p.getId(), 0);

            BigDecimal conv = p.getConversionFactor() != null && p.getConversionFactor().compareTo(BigDecimal.ZERO) > 0
                    ? p.getConversionFactor()
                    : BigDecimal.ONE;

            List<ServiceSupply> productSupplies = serviceSupplyRepository.findAll().stream()
                    .filter(ss -> ss.getProduct().getId().equals(p.getId()))
                    .toList();

            BigDecimal avgConsumptionPerService = BigDecimal.ZERO;
            if (!productSupplies.isEmpty()) {
                BigDecimal totalQty = productSupplies.stream().map(ServiceSupply::getQuantityConsumptionUnit).reduce(BigDecimal.ZERO, BigDecimal::add);
                avgConsumptionPerService = totalQty.divide(BigDecimal.valueOf(productSupplies.size()), 4, RoundingMode.HALF_UP);
            }

            int servicesRemaining = 0;
            if (avgConsumptionPerService.compareTo(BigDecimal.ZERO) > 0 && currentStock.compareTo(BigDecimal.ZERO) > 0) {
                servicesRemaining = currentStock.divide(avgConsumptionPerService, 0, RoundingMode.DOWN).intValue();
            }

            BigDecimal suggestedPurchase = BigDecimal.ZERO;
            BigDecimal restockCost = BigDecimal.ZERO;
            String status = "OK";

            if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
                status = "CRITICO_7_DIAS";
                BigDecimal neededConsumption = balanceAfter.abs();
                suggestedPurchase = neededConsumption.divide(conv, 0, RoundingMode.UP);
                if (suggestedPurchase.compareTo(BigDecimal.ZERO) == 0) suggestedPurchase = BigDecimal.ONE;
                BigDecimal purchasePrice = p.getPurchasePrice() != null ? p.getPurchasePrice() : BigDecimal.ZERO;
                restockCost = suggestedPurchase.multiply(purchasePrice).setScale(2, RoundingMode.HALF_UP);
            } else if (servicesRemaining <= 5 && servicesRemaining > 0) {
                status = "BAJO";
            } else if (currentStock.compareTo(BigDecimal.ZERO) <= 0) {
                status = "SIN_STOCK";
            }

            result.add(StockProjectionDto.builder()
                    .productId(p.getId())
                    .productName(p.getName())
                    .purchaseUnit(p.getPurchaseUnit())
                    .consumptionUnit(p.getConsumptionUnit())
                    .conversionFactor(conv)
                    .currentStockConsumptionUnit(currentStock)
                    .projectedDemand7Days(demand)
                    .balanceAfter7Days(balanceAfter)
                    .appointmentsNext7Days(aptCount)
                    .servicesRemainingWithStock(servicesRemaining)
                    .suggestedPurchaseQuantity(suggestedPurchase)
                    .estimatedRestockCost(restockCost)
                    .status(status)
                    .build());
        }

        return result;
    }

    /**
     * Product purchase price history and variation analysis.
     */
    @Transactional(readOnly = true)
    public ProductPriceAnalysisDto getProductPriceAnalysis(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no existe: " + productId));

        List<InventoryMovement> movements = movementRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .filter(m -> m.getMovementType() == MovementType.PURCHASE)
                .toList();

        List<ProductPriceAnalysisDto.PurchaseHistoryEntryDto> history = new ArrayList<>();
        BigDecimal totalSpent = BigDecimal.ZERO;
        BigDecimal totalPurchasedUnits = BigDecimal.ZERO;
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;

        List<InventoryMovement> ascMovements = new ArrayList<>(movements);
        Collections.reverse(ascMovements);

        BigDecimal prevPrice = null;
        for (InventoryMovement m : ascMovements) {
            BigDecimal price = m.getUnitPurchasePrice() != null ? m.getUnitPurchasePrice() : BigDecimal.ZERO;
            BigDecimal qty = m.getQuantityPurchaseUnit() != null ? m.getQuantityPurchaseUnit() : BigDecimal.ONE;
            BigDecimal totalPaid = price.multiply(qty).setScale(2, RoundingMode.HALF_UP);

            totalSpent = totalSpent.add(totalPaid);
            totalPurchasedUnits = totalPurchasedUnits.add(qty);

            if (minPrice == null || price.compareTo(minPrice) < 0) minPrice = price;
            if (maxPrice == null || price.compareTo(maxPrice) > 0) maxPrice = price;

            BigDecimal variation = BigDecimal.ZERO;
            if (prevPrice != null && prevPrice.compareTo(BigDecimal.ZERO) > 0) {
                variation = price.subtract(prevPrice).multiply(BigDecimal.valueOf(100)).divide(prevPrice, 2, RoundingMode.HALF_UP);
            }

            history.add(ProductPriceAnalysisDto.PurchaseHistoryEntryDto.builder()
                    .movementId(m.getId())
                    .purchaseQuantity(qty)
                    .purchaseUnit(product.getPurchaseUnit())
                    .unitPurchasePrice(price)
                    .totalPaid(totalPaid)
                    .reference(m.getReference())
                    .purchaseDate(m.getCreatedAt())
                    .variationFromPrevious(variation)
                    .build());

            prevPrice = price;
        }

        Collections.reverse(history);

        BigDecimal lastPrice = product.getPurchasePrice();
        BigDecimal previousPrice = null;
        BigDecimal delta = BigDecimal.ZERO;
        BigDecimal varPct = BigDecimal.ZERO;
        String trend = "INITIAL";

        if (history.size() >= 2) {
            lastPrice = history.get(0).getUnitPurchasePrice();
            previousPrice = history.get(1).getUnitPurchasePrice();
            delta = lastPrice.subtract(previousPrice);
            if (previousPrice.compareTo(BigDecimal.ZERO) > 0) {
                varPct = delta.multiply(BigDecimal.valueOf(100)).divide(previousPrice, 2, RoundingMode.HALF_UP);
            }
            if (delta.compareTo(BigDecimal.ZERO) > 0) trend = "UP";
            else if (delta.compareTo(BigDecimal.ZERO) < 0) trend = "DOWN";
            else trend = "EQUAL";
        } else if (history.size() == 1) {
            lastPrice = history.get(0).getUnitPurchasePrice();
        }

        BigDecimal avgPrice = BigDecimal.ZERO;
        if (totalPurchasedUnits.compareTo(BigDecimal.ZERO) > 0) {
            avgPrice = totalSpent.divide(totalPurchasedUnits, 2, RoundingMode.HALF_UP);
        } else if (lastPrice != null) {
            avgPrice = lastPrice;
        }

        return ProductPriceAnalysisDto.builder()
                .productId(product.getId())
                .productName(product.getName())
                .lastPurchasePrice(lastPrice)
                .previousPurchasePrice(previousPrice)
                .priceDelta(delta)
                .priceVariationPercentage(varPct)
                .trend(trend)
                .averagePurchasePrice(avgPrice)
                .minPurchasePrice(minPrice != null ? minPrice : lastPrice)
                .maxPurchasePrice(maxPrice != null ? maxPrice : lastPrice)
                .totalPurchasesCount(history.size())
                .purchaseHistory(history)
                .build();
    }
}
