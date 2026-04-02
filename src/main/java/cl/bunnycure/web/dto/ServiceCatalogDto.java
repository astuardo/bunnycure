package cl.bunnycure.web.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceCatalogDto {

    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ0-9\\s+\\-/().,]+$", 
             message = "El nombre solo puede contener letras, números y símbolos básicos (+, -, /, (), .)")
    private String name;

    @NotNull(message = "La duración es obligatoria")
    @Min(value = 15, message = "La duración mínima es 15 minutos")
    @Max(value = 480, message = "La duración máxima es 8 horas (480 minutos)")
    private Integer durationMinutes;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.01", inclusive = true, message = "El precio mínimo es $0.01")
    @DecimalMax(value = "9999999.99", inclusive = true, message = "El precio máximo es $9,999,999.99")
    @Digits(integer = 7, fraction = 2, message = "El precio debe tener máximo 7 dígitos enteros y 2 decimales")
    private BigDecimal price;

    @Size(max = 300, message = "La descripción no puede superar los 300 caracteres")
    private String description;

    @Builder.Default
    private Boolean active = true;

    @Min(value = 0, message = "El orden de aparición no puede ser negativo")
    @Max(value = 9999, message = "El orden de aparición no puede superar 9999")
    @Builder.Default
    private Integer displayOrder = 0;
}