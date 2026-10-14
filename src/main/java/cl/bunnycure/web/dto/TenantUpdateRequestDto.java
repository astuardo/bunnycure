package cl.bunnycure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUpdateRequestDto {

    @NotBlank(message = "El nombre del salón es obligatorio")
    @Size(max = 120, message = "El nombre no puede exceder 120 caracteres")
    private String name;

    @Size(max = 120, message = "El dominio no puede exceder 120 caracteres")
    private String customDomain;

    private String rut;
    private String phone;
    private String email;
    private String address;
    private String logoUrl;
    private String primaryColor;
    private String planTier;
    private String whatsappPhoneId;
    private String whatsappToken;
    private String siiRut;
    private String siiPassword;
    private String siiToken;
    private String siiActividadEconomica;
    private Boolean active;
}
