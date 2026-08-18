package cl.bunnycure.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCustomerServiceRecordRequestDto {
    @NotBlank(message = "El detalle del servicio o técnica es obligatorio")
    private String serviceDetail;
    private String photoCaption;
    private String photoBase64;
    private String mimeType;
}
