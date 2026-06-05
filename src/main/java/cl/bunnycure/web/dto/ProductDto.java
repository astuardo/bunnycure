package cl.bunnycure.web.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductDto {

    @NotBlank
    private String name;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal purchasePrice;

    private String purchaseUrl;

    @NotBlank
    private String purchaseUnit;

    @NotBlank
    private String consumptionUnit;

    @NotNull
    @DecimalMin(value = "0.0001", inclusive = true)
    private BigDecimal conversionFactor;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal stockConsumptionUnit;
}
