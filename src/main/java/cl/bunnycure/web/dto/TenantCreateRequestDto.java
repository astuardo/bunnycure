package cl.bunnycure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantCreateRequestDto {

    @NotBlank(message = "El nombre del salón es obligatorio")
    @Size(max = 120, message = "El nombre no puede exceder 120 caracteres")
    private String name;

    @NotBlank(message = "El identificador slug es obligatorio")
    @Size(min = 2, max = 60, message = "El slug debe tener entre 2 y 60 caracteres")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "El slug solo puede contener letras minúsculas, números y guiones")
    private String slug;

    @Size(max = 120, message = "El dominio no puede exceder 120 caracteres")
    private String customDomain;

    private String rut;
    private String phone;
    private String email;
    private String address;
    private String logoUrl;

    @Builder.Default
    private String primaryColor = "#d48b70";

    @Builder.Default
    private String planTier = "STANDARD";

    private String whatsappPhoneId;
    private String whatsappToken;

    private String siiRut;
    private String siiPassword;
    private String siiToken;
    private String siiActividadEconomica;

    // Datos opcionales para aprovisionamiento del administrador inicial
    private String initialAdminUsername;
    private String initialAdminPassword;
    private String initialAdminFullName;
    private String initialAdminEmail;
}
