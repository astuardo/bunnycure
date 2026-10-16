package cl.bunnycure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO para representar errores en respuestas de API REST.
 * Incluye mensaje, código de error y detalles opcionales (para validaciones).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    private String message;
    private String errorCode;
    private List<FieldError> fieldErrors;
    /** Datos adicionales para el cliente (p.ej. plan requerido en errores 402). */
    private java.util.Map<String, Object> details;
    
    public ErrorResponse(String message, String errorCode) {
        this.message = message;
        this.errorCode = errorCode;
    }
    
    public ErrorResponse(String message, String errorCode, List<FieldError> fieldErrors) {
        this.message = message;
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors;
    }
    
    public ErrorResponse(String message, String errorCode, java.util.Map<String, Object> details) {
        this.message = message;
        this.errorCode = errorCode;
        this.details = details;
    }

    public java.util.Map<String, Object> getDetails() {
        return details;
    }

    public void addFieldError(String field, String message) {
        if (fieldErrors == null) {
            fieldErrors = new ArrayList<>();
        }
        fieldErrors.add(new FieldError(field, message));
    }
    
    // Getters
    public String getMessage() {
        return message;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }
    
    /**
     * Representa un error de validación en un campo específico.
     */
    public static class FieldError {
        private String field;
        private String message;
        
        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }
        
        public String getField() {
            return field;
        }
        
        public String getMessage() {
            return message;
        }
    }
}
