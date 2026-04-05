package cl.bunnycure.web.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO para actualizar múltiples configuraciones en una sola operación.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUpdateSettingsRequest {
    
    @NotEmpty(message = "Debe proporcionar al menos una configuración")
    private Map<String, String> settings;
}
