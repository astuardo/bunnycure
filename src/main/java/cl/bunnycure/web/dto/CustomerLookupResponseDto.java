package cl.bunnycure.web.dto;

/**
 * DTO para la respuesta de búsqueda de clienta por teléfono.
 * Si la clienta existe, retorna sus datos. Si no, todos los campos son null.
 */
public class CustomerLookupResponseDto {

    private Long id;
    private String fullName;
    private String phone;
    private String email;
    private String gender;
    private java.time.LocalDate birthDate;
    private String emergencyPhone;
    private String healthNotes;
    private boolean found;

    public CustomerLookupResponseDto() {
        this.found = false;
    }

    public CustomerLookupResponseDto(Long id,
                                     String fullName,
                                     String phone,
                                     String email,
                                     String gender,
                                     java.time.LocalDate birthDate,
                                     String emergencyPhone,
                                     String healthNotes) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.gender = gender;
        this.birthDate = birthDate;
        this.emergencyPhone = emergencyPhone;
        this.healthNotes = healthNotes;
        this.found = true;
    }

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public java.time.LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(java.time.LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getEmergencyPhone() {
        return emergencyPhone;
    }

    public void setEmergencyPhone(String emergencyPhone) {
        this.emergencyPhone = emergencyPhone;
    }

    public String getHealthNotes() {
        return healthNotes;
    }

    public void setHealthNotes(String healthNotes) {
        this.healthNotes = healthNotes;
    }

    public boolean isFound() {
        return found;
    }

    public void setFound(boolean found) {
        this.found = found;
    }
}
