package cl.bunnycure.domain.enums;

/**
 * Ciclo de vida de la suscripción de un salón.
 * PENDING_VERIFICATION → TRIALING → ACTIVE ⇄ PAST_DUE (gracia) → SUSPENDED; CANCELED es terminal.
 */
public enum SubscriptionStatus {
    /** Registro autoservicio: falta verificar el email de la administradora. */
    PENDING_VERIFICATION,
    TRIALING,
    ACTIVE,
    /** Período vencido sin pago: sigue operando durante los días de gracia. */
    PAST_DUE,
    SUSPENDED,
    CANCELED;

    /** Puede usar la aplicación normalmente. */
    public boolean allowsAccess() {
        return this == TRIALING || this == ACTIVE || this == PAST_DUE;
    }
}
