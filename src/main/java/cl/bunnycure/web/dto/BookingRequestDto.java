package cl.bunnycure.web.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.Period;

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

    // Getters & Setters
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }
    public java.time.LocalDate getPreferredDate() { return preferredDate; }
    public void setPreferredDate(java.time.LocalDate preferredDate) { this.preferredDate = preferredDate; }
    public String getPreferredBlock() { return preferredBlock; }
    public void setPreferredBlock(String preferredBlock) { this.preferredBlock = preferredBlock; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getEmergencyPhone() { return emergencyPhone; }
    public void setEmergencyPhone(String emergencyPhone) { this.emergencyPhone = emergencyPhone; }
}
