package cl.bunnycure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para servicios del catálogo.
 * Incluye toda la información del servicio para la API REST.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta con datos completos de un servicio del catálogo")
public class ServiceCatalogResponseDto {
    
    @Schema(description = "ID del servicio", example = "1")
    private Long id;
    
    @Schema(description = "Nombre del servicio", example = "Manicure Gel")
    private String name;
    
    @Schema(description = "Descripción del servicio")
    private String description;
    
    @Schema(description = "Duración en minutos", example = "60")
    private Integer durationMinutes;
    
    @Schema(description = "Precio del servicio", example = "25000")
    private java.math.BigDecimal price;
    
    @Schema(description = "Si el servicio está activo")
    private boolean active;
    
    @Schema(description = "Orden de visualización", example = "1")
    private Integer displayOrder;
    
    @Schema(description = "URL de la imagen del servicio")
    private String imageUrl;
}
