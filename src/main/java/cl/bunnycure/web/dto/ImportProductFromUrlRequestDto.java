package cl.bunnycure.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ImportProductFromUrlRequestDto {

    @NotBlank
    private String purchaseUrl;
}

