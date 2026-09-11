package cl.bunnycure.web.dto.marketing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignDispatchRequestDto {

    @NotBlank(message = "El nombre de la plantilla es obligatorio")
    private String templateName;

    @NotNull(message = "El tipo de audiencia es obligatorio")
    private AudienceType audienceType;

    /**
     * Opcional: teléfono de prueba. Si se especifica, solo se enviará a este número en vez de a la audiencia completa.
     */
    private String testPhoneNumber;
}
