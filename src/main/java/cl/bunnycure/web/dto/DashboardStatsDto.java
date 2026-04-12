package cl.bunnycure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {
    private BigDecimal totalRevenueMonth;
    private Long totalAppointmentsMonth;
    private List<ServiceStatDto> topServices;
    private CustomerStatDto topCustomer;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceStatDto {
        private String name;
        private Long count;
        private BigDecimal revenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerStatDto {
        private String name;
        private Long appointmentCount;
        private BigDecimal totalSpent;
    }
}
