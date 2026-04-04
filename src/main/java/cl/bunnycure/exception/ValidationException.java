package cl.bunnycure.exception;

/**
 * Excepción lanzada cuando falla la validación de datos de negocio.
 * Diferente de @Valid que valida estructura, esta valida reglas de negocio.
 */
public class ValidationException extends ServiceException {
    
    public ValidationException(String message) {
        super(message, "VALIDATION_ERROR");
    }
    
    public ValidationException(String message, Throwable cause) {
        super(message, "VALIDATION_ERROR", cause);
    }
    
    public ValidationException(String field, String message) {
        super(String.format("Validation error in field '%s': %s", field, message), "VALIDATION_ERROR");
    }
}
