package cl.bunnycure.web.dto.marketing;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTemplateGenerateRequestDto {

    @NotBlank(message = "El prompt o instrucción para la plantilla es obligatorio")
    private String prompt;

    @Builder.Default
    private boolean autoRegisterInMeta = true;
}
