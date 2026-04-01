package cl.bunnycure.domain.enums;

/**
 * Preferencia de notificación del cliente.
 * Define qué canal(es) de comunicación prefiere el cliente para recibir notificaciones.
 */
public enum NotificationPreference {
    /**
     * Recibir notificaciones solo por email.
     */
    EMAIL_ONLY("Solo Email"),
    
    /**
     * Recibir notificaciones solo por WhatsApp.
     */
    WHATSAPP_ONLY("Solo WhatsApp"),
    
    /**
     * Recibir notificaciones por ambos canales (email y WhatsApp).
     */
    BOTH("Email y WhatsApp"),
    
    /**
     * No recibir notificaciones automáticas.
     */
    NONE("Sin notificaciones");
    
    private final String displayName;
    
    NotificationPreference(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Verifica si el cliente debe recibir notificaciones por email según su preferencia.
     * @return true si la preferencia incluye email
     */
    public boolean allowsEmail() {
        return this == EMAIL_ONLY || this == BOTH;
    }
    
    /**
     * Verifica si el cliente debe recibir notificaciones por WhatsApp según su preferencia.
     * @return true si la preferencia incluye WhatsApp
     */
    public boolean allowsWhatsApp() {
        return this == WHATSAPP_ONLY || this == BOTH;
    }
    
    /**
     * Verifica si el cliente ha optado por no recibir ninguna notificación.
     * @return true si no quiere notificaciones
     */
    public boolean isOptedOut() {
        return this == NONE;
    }
}
