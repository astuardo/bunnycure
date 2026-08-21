package cl.bunnycure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecialistStatsDto {
    private Long specialistId;
    private String specialistName;
    private long totalCount;
    private long completedCount;
    private long cancelledCount;
    private BigDecimal revenue;
    private int completionRate;
}
