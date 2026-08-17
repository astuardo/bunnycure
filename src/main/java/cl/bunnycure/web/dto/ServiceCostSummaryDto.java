package cl.bunnycure.web.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceCostSummaryDto {

    private Long serviceId;
    private String serviceName;
    private BigDecimal servicePrice;
    private BigDecimal totalMaterialsCost;
    private BigDecimal grossMargin;
    private BigDecimal grossMarginPercentage;
    private List<ServiceSupplyResponseDto> supplies;
}
