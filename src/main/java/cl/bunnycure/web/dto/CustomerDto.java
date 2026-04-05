package cl.bunnycure.web.dto;

import cl.bunnycure.domain.enums.NotificationPreference;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Email(message = "Email inválido")
    private String email;  // Ahora es opcional

    @Pattern(regexp = "^$|^(?i)(M|F|MASCULINO|FEMENINO|MALE|FEMALE)$", message = "El género debe ser M, F, Masculino o Femenino")
    private String gender;

    private LocalDate birthDate;

    @Pattern(regexp = "^$|^\\+?[0-9]{8,15}$", message = "Formato de teléfono de emergencia inválido")
    private String emergencyPhone;

    @Size(max = 500)
    private String healthNotes;

    @Size(max = 500)
    private String notes;

    @NotNull(message = "La preferencia de notificación es obligatoria")
    private NotificationPreference notificationPreference;
}