package cl.bunnycure.exception;

/**
 * Excepción lanzada cuando un recurso solicitado no existe.
 * Extiende de ServiceException para manejo consistente.
 */
public class ResourceNotFoundException extends ServiceException {
    
    public ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND");
    }
    
    public ResourceNotFoundException(String resourceType, Long id) {
        super(String.format("%s with ID %d not found", resourceType, id), "RESOURCE_NOT_FOUND");
    }
    
    public ResourceNotFoundException(String resourceType, String identifier) {
        super(String.format("%s with identifier '%s' not found", resourceType, identifier), "RESOURCE_NOT_FOUND");
    }
}
