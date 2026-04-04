package cl.bunnycure.exception;

/**
 * Excepción base para todas las excepciones de la capa de servicios.
 * Permite manejo centralizado y consistente de errores en la API REST.
 */
public class ServiceException extends RuntimeException {
    
    private final String errorCode;
    
    public ServiceException(String message) {
        super(message);
        this.errorCode = "SERVICE_ERROR";
    }
    
    public ServiceException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public ServiceException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "SERVICE_ERROR";
    }
    
    public ServiceException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}
