package cl.bunnycure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para respuesta de login exitoso.
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
     * Indica si el usuario debe cambiar su contraseña.
     */
    private boolean requiresPasswordChange;
    
    /**
     * Mensaje adicional (opcional).
     */
    private String message;
}
