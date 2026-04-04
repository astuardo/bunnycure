package cl.bunnycure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO resumido de servicio para incluir en otras respuestas.
 * Evita cargar toda la información del servicio cuando no es necesario.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resumen de un servicio")
public class ServiceSummaryDto {
    
    @Schema(description = "ID del servicio", example = "1")
    private Long id;
    
    @Schema(description = "Nombre del servicio", example = "Manicure Gel")
    private String name;
    
    @Schema(description = "Duración en minutos", example = "60")
    private Integer durationMinutes;
    
    @Schema(description = "Precio del servicio", example = "25000")
    private java.math.BigDecimal price;
    
    @Schema(description = "Si el servicio está activo")
    private boolean active;
}
