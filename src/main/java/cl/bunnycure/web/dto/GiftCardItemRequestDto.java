package cl.bunnycure.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GiftCardItemRequestDto {

    @NotNull(message = "serviceId es obligatorio")
    private Long serviceId;

    @NotNull(message = "quantity es obligatorio")
    @Min(value = 1, message = "quantity debe ser al menos 1")
    private Integer quantity;
}
