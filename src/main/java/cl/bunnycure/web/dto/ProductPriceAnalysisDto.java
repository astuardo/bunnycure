package cl.bunnycure.web.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPriceAnalysisDto {

    private Long productId;
    private String productName;
    private BigDecimal lastPurchasePrice;
    private BigDecimal previousPurchasePrice;
    private BigDecimal priceDelta;
    private BigDecimal priceVariationPercentage;
    private String trend; // "UP", "DOWN", "EQUAL", "INITIAL"
    private BigDecimal averagePurchasePrice;
    private BigDecimal minPurchasePrice;
    private BigDecimal maxPurchasePrice;
    private Integer totalPurchasesCount;
    private List<PurchaseHistoryEntryDto> purchaseHistory;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PurchaseHistoryEntryDto {
        private Long movementId;
        private BigDecimal purchaseQuantity;
        private String purchaseUnit;
        private BigDecimal unitPurchasePrice;
        private BigDecimal totalPaid;
        private String reference;
        private OffsetDateTime purchaseDate;
        private BigDecimal variationFromPrevious;
    }
}
