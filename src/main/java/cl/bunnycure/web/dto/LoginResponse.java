package cl.bunnycure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para respuesta de login exitoso.
 * Incluye JWT para autenticación móvil.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    
    /**
     * Información del usuario autenticado.
     */
    private UserDto user;
    
    /**
     * Token JWT para autenticación en requests subsiguientes.
     * El cliente móvil debe enviar este token en el header:
     * Authorization: Bearer {token}
     */
    private String token;
    
    /**
     * Indica si el usuario debe cambiar su contraseña.
     */
    private boolean requiresPasswordChange;
    
    /**
     * Mensaje adicional (opcional).
     */
    private String message;
}
