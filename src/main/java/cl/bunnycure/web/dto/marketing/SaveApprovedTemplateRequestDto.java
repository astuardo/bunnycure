package cl.bunnycure.web.dto.marketing;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO para persistir y registrar una plantilla de marketing tras la revisión y visto bueno del usuario.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveApprovedTemplateRequestDto {

    private String name;

    @NotBlank(message = "El nombre visible de la campaña es obligatorio")
    private String displayName;

    private String occasion;

    private String emoji;

    private String headerText;

    @NotBlank(message = "El cuerpo del mensaje es obligatorio")
    private String bodyText;

    private String footerText;

    private String buttonText;

    private String buttonUrl;

    private List<String> sampleVariables;

    @Builder.Default
    private boolean autoRegisterInMeta = true;
}
