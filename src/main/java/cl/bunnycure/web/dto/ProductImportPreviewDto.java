package cl.bunnycure.web.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProductImportPreviewDto {
    private String name;
    private String purchaseUrl;
    private BigDecimal purchasePrice;
    private BigDecimal observedPrice;
    private Boolean observedAvailable;
}

