package cl.bunnycure.web.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockProjectionDto {

    private Long productId;
    private String productName;
    private String purchaseUnit;
    private String consumptionUnit;
    private BigDecimal conversionFactor;
    private BigDecimal currentStockConsumptionUnit;
    private BigDecimal projectedDemand7Days;
    private BigDecimal balanceAfter7Days;
    private Integer appointmentsNext7Days;
    private Integer servicesRemainingWithStock;
    private BigDecimal suggestedPurchaseQuantity; // in purchase units
    private BigDecimal estimatedRestockCost;
    private String status; // "OK", "BAJO", "CRITICO_7_DIAS"
}
