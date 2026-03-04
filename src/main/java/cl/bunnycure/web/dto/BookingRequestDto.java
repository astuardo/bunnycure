package cl.bunnycure.web.dto;

import jakarta.validation.constraints.*;

public class BookingRequestDto {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 100)
    private String fullName;

    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(regexp = "^\\+56[0-9]{9}$", message = "El teléfono debe ser +56 seguido de 9 dígitos")
    private String phone;

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

    // Getters & Setters
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
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
}
