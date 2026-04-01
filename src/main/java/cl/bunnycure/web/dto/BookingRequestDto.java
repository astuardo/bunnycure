package cl.bunnycure.web.dto;

import cl.bunnycure.domain.enums.NotificationPreference;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.Period;

@Getter
@Setter
@NoArgsConstructor
public class BookingRequestDto {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 100)
    private String fullName;

    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(regexp = "^\\+56[0-9]{9}$", message = "El teléfono debe ser +56 seguido de 9 dígitos")
    private String phone;

    @NotBlank(message = "El género es obligatorio")
    @Pattern(regexp = "^(?i)(MASCULINO|FEMENINO)$", message = "El género debe ser Masculino o Femenino")
    private String gender;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    private LocalDate birthDate;

    @Email(message = "Email inválido")
    private String email;

    @NotNull(message = "Debes seleccionar un servicio")
    private Long serviceId;

    @NotNull(message = "La fecha es obligatoria")
    private java.time.LocalDate preferredDate;

    @NotBlank(message = "Selecciona un bloque horario")
    private String preferredBlock;

    @Size(max = 500)
    private String notes;

    @Pattern(regexp = "^$|^\\+56[0-9]{9}$", message = "El teléfono de emergencia debe ser +56 seguido de 9 dígitos")
    private String emergencyPhone;

    @NotNull(message = "La preferencia de notificación es obligatoria")
    private NotificationPreference notificationPreference = NotificationPreference.BOTH;

    @AssertTrue(message = "La edad mínima es 16 años para género masculino y 14 años para género femenino")
    public boolean isMinimumAgeValidByGender() {
        if (gender == null || birthDate == null) {
            return false;
        }

        int age = Period.between(birthDate, LocalDate.now()).getYears();
        String normalizedGender = gender.trim().toUpperCase();

        if ("MASCULINO".equals(normalizedGender)) {
            return age >= 16;
        }
        if ("FEMENINO".equals(normalizedGender)) {
            return age >= 14;
        }
        return false;
    }

}
