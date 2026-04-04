package cl.bunnycure.exception;

/**
 * Excepción lanzada cuando existe un conflicto de estado o datos.
 * Ejemplo: intentar aprobar una cita ya aprobada, eliminar un servicio en uso, etc.
 */
public class ConflictException extends ServiceException {
    
    public ConflictException(String message) {
        super(message, "CONFLICT");
    }
    
    public ConflictException(String message, Throwable cause) {
        super(message, "CONFLICT", cause);
    }
}
