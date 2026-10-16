package cl.bunnycure.domain.enums;

/**
 * Funcionalidades comerciales que dependen del plan del salón.
 * Lo que no aparece aquí (agenda, clientas, reservas, fidelización básica, recordatorios
 * por email/push) está incluido en todos los planes.
 */
public enum PlanFeature {
    WHATSAPP_AUTOMATION("Recordatorios y bot de WhatsApp"),
    SII_INVOICING("Boletas de honorarios SII"),
    WALLET_PASSES("Tarjeta de sellos en Apple y Google Wallet"),
    GIFT_CARDS("Gift cards digitales"),
    INVENTORY("Control de insumos e inventario"),
    COMMISSIONS("Cálculo de comisiones"),
    MARKETING_CAMPAIGNS("Campañas de marketing"),
    MULTI_LOCATION("Múltiples sucursales"),
    CUSTOM_DOMAIN("Dominio propio");

    private final String displayName;

    PlanFeature(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
