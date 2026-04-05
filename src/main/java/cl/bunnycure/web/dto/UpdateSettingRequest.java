package cl.bunnycure.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para actualizar una configuración individual.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettingRequest {
    
    @NotBlank(message = "El valor no puede estar vacío")
    private String value;
}
