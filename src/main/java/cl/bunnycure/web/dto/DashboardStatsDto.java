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
    private BigDecimal totalRevenueMonth;          // Proyectado (citas activas)
    private BigDecimal completedRevenueMonth;      // Real cobrado (citas completadas)
    private BigDecimal projectedRevenueMonth;      // Alias explícito
    private Long totalAppointmentsMonth;           // Total de citas activas del mes
    private Long completedAppointmentsMonth;       // Citas completadas en el mes
    private Long pendingOrConfirmedAppointmentsMonth; // Citas por atender en el mes
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
