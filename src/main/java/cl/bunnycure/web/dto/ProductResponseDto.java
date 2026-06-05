package cl.bunnycure.web.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
public class ProductResponseDto {
    private Long id;
    private String name;
    private BigDecimal purchasePrice;
    private String purchaseUrl;
    private String purchaseUnit;
    private String consumptionUnit;
    private BigDecimal conversionFactor;
    private BigDecimal stockConsumptionUnit;
    private BigDecimal observedPrice;
    private BigDecimal previousObservedPrice;
    private Boolean observedAvailable;
    private OffsetDateTime lastObservedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
