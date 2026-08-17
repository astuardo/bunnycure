package cl.bunnycure.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceSupplyDto {

    private Long id;

    @NotNull(message = "El ID del producto es obligatorio")
    private Long productId;

    @NotNull(message = "La cantidad es obligatoria")
    @DecimalMin(value = "0.0001", message = "La cantidad debe ser mayor a 0")
    private BigDecimal quantityConsumptionUnit;
}
