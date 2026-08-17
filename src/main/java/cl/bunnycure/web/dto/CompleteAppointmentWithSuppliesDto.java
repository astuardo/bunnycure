package cl.bunnycure.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompleteAppointmentWithSuppliesDto {

    @NotNull(message = "El ID de la cita es obligatorio")
    private Long appointmentId;

    private Boolean generateInvoice;

    @Builder.Default
    private Boolean deductSupplies = true;

    private List<SuppliesUsageDto> supplies;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SuppliesUsageDto {
        private Long productId;
        private BigDecimal quantity; // in consumption unit
    }
}
