package cl.bunnycure.web.dto.marketing;

public enum AudienceType {
    ALL("Todas las clientas registradas con WhatsApp"),
    INACTIVE_30_DAYS("Clientas inactivas (sin visita hace más de 30 días)"),
    INACTIVE_60_DAYS("Clientas inactivas (sin visita hace más de 60 días)"),
    ACTIVE_RECENT("Clientas activas recientes (visita en los últimos 45 días)"),
    FREQUENT_VIP("Clientas VIP / Frecuentes (3 o más visitas completadas)"),
    BIRTHDAYS_TODAY("Clientas que cumplen años hoy"),
    BIRTHDAYS_THIS_MONTH("Clientas que cumplen años este mes");

    private final String description;

    AudienceType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
