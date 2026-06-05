package cl.bunnycure.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PurchaseRequestDto {
    @NotNull
    private Long productId;

    @NotNull
    @DecimalMin(value = "0.0001", inclusive = true)
    private BigDecimal purchaseQuantity; // in purchase units

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal unitPurchasePrice; // price per purchase unit

    private String reference;
    private Long createdBy;
}
