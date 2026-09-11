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
public class TemplateUpdateRequestDto {
    private String headerText;

    @NotBlank(message = "El texto del cuerpo es obligatorio")
    private String bodyText;

    private String footerText;
    private String buttonText;
    private String buttonUrl;
}
