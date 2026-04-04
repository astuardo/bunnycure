package cl.bunnycure.exception;

/**
 * Excepción lanzada cuando un usuario no tiene permisos para realizar una acción.
 */
public class UnauthorizedException extends ServiceException {
    
    public UnauthorizedException(String message) {
        super(message, "UNAUTHORIZED");
    }
    
    public UnauthorizedException(String message, Throwable cause) {
        super(message, "UNAUTHORIZED", cause);
    }
}
