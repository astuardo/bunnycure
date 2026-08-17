package cl.bunnycure.web.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceSupplyResponseDto {

    private Long id;
    private Long serviceId;
    private Long productId;
    private String productName;
    private String purchaseUnit;
    private String consumptionUnit;
    private BigDecimal conversionFactor;
    private BigDecimal quantityConsumptionUnit;
    private BigDecimal productPurchasePrice;
    private BigDecimal unitConsumptionCost;
    private BigDecimal totalEstimatedCost;
    private BigDecimal currentStock;
}
