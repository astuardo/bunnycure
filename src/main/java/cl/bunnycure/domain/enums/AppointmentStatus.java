package cl.bunnycure.domain.enums;

public enum AppointmentStatus {
    PENDING("Pendiente"),
    COMPLETED("Completado"),
    CANCELLED("Cancelado");

    private final String displayName;

    AppointmentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
