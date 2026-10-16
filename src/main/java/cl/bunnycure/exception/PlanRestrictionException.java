package cl.bunnycure.exception;

import java.util.Map;

/**
 * El plan o el estado de la suscripción del salón no permiten la operación.
 * Se responde con HTTP 402 Payment Required y un código estable para el frontend:
 * PLAN_FEATURE_REQUIRED, PLAN_LIMIT_REACHED o SUBSCRIPTION_SUSPENDED.
 */
public class PlanRestrictionException extends RuntimeException {

    private final String errorCode;
    private final Map<String, Object> details;

    public PlanRestrictionException(String errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
