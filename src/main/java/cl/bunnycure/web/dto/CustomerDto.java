package cl.bunnycure.web.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDto {

    private Long id;

    private String publicId;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    private String fullName;

    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Formato de teléfono inválido")
    private String phone;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Email inválido")
    private String email;

    @Pattern(regexp = "^$|^(?i)(MASCULINO|FEMENINO)$", message = "El género debe ser Masculino o Femenino")
    private String gender;

    private LocalDate birthDate;

    @Pattern(regexp = "^$|^\\+56[0-9]{9}$", message = "El teléfono de emergencia debe ser +56 seguido de 9 dígitos")
    private String emergencyPhone;

    @Size(max = 500)
    private String healthNotes;

    @Size(max = 500)
    private String notes;
}