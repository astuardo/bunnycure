package cl.bunnycure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO para la respuesta de búsqueda de clienta por teléfono en endpoints públicos (reservas / gift cards).
 * Por privacidad y seguridad (normativas de protección de datos), solo se exponen los datos básicos de
 * identificación necesarios para asociar la cita o tarjeta, omitiendo notas de salud y teléfonos de emergencia.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerLookupResponseDto {

    private Long id;
    private String fullName;
    private String phone;
    private String email;
    private boolean found;

    public static CustomerLookupResponseDto found(Long id,
                                                  String fullName,
                                                  String phone,
                                                  String email) {
        CustomerLookupResponseDto dto = new CustomerLookupResponseDto();
        dto.id = id;
        dto.fullName = fullName;
        dto.phone = phone;
        dto.email = email;
        dto.found = true;
        return dto;
    }

    /**
     * Constructor de compatibilidad: ignora intencionalmente los campos sensibles por seguridad.
     */
    @Deprecated
    public static CustomerLookupResponseDto found(Long id,
                                                  String fullName,
                                                  String phone,
                                                  String email,
                                                  String gender,
                                                  java.time.LocalDate birthDate,
                                                  String emergencyPhone,
                                                  String healthNotes) {
        return found(id, fullName, phone, email);
    }
}