package cl.bunnycure.web.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * DTO para la respuesta de búsqueda de clienta por teléfono.
 * Si la clienta existe, retorna sus datos. Si no, todos los campos son null.
 */
@Getter
@Setter
@NoArgsConstructor
public class CustomerLookupResponseDto {

    private Long id;
    private String fullName;
    private String phone;
    private String email;
    private String gender;
    private LocalDate birthDate;
    private String emergencyPhone;
    private String healthNotes;
    private boolean found;

    public static CustomerLookupResponseDto found(Long id,
                                                  String fullName,
                                                  String phone,
                                                  String email,
                                                  String gender,
                                                  LocalDate birthDate,
                                                  String emergencyPhone,
                                                  String healthNotes) {
        CustomerLookupResponseDto dto = new CustomerLookupResponseDto();
        dto.id = id;
        dto.fullName = fullName;
        dto.phone = phone;
        dto.email = email;
        dto.gender = gender;
        dto.birthDate = birthDate;
        dto.emergencyPhone = emergencyPhone;
        dto.healthNotes = healthNotes;
        dto.found = true;
        return dto;
    }
}