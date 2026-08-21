package cl.bunnycure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodayOperationalStatsDto {
    private LocalDate date;
    private long totalAppointments;
    private long completedCount;
    private long pendingCount;
    private long confirmedCount;
    private long cancelledCount;
    private long inProgressOrUpcoming2HoursCount;
    private long potentialNoShowCount;
    private BigDecimal collectedRevenue;
    private BigDecimal projectedRevenue;
    private int completionRate;
    private LocalTime nextAppointmentTime;
    private String nextCustomerName;
    private String nextServiceName;
    private String nextSpecialistName;
}
