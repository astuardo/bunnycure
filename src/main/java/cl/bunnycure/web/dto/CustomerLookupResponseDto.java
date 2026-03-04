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
    private boolean found;

    public CustomerLookupResponseDto() {
        this.found = false;
    }

    public CustomerLookupResponseDto(Long id, String fullName, String phone, String email) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
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

    public boolean isFound() {
        return found;
    }

    public void setFound(boolean found) {
        this.found = found;
    }
}
