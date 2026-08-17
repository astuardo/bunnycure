package cl.bunnycure.web.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentSuppliesPreviewDto {

    private Long appointmentId;
    private Long customerId;
    private String customerName;
    private String appointmentDate;
    private String appointmentTime;
    private List<String> serviceNames;
    private boolean autoConsumptionEnabled;
    private List<AppointmentSupplyItemDto> supplies;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AppointmentSupplyItemDto {
        private Long productId;
        private String productName;
        private String consumptionUnit;
        private BigDecimal suggestedQuantity;
        private BigDecimal currentStock;
        private BigDecimal projectedStockAfter;
        private BigDecimal unitConsumptionCost;
        private BigDecimal estimatedCost;
    }
}
