package cl.bunnycure.domain.enums;

public enum BookingRequestStatus {
    PENDING("Pendiente"),
    APPROVED("Aprobada"),
    REJECTED("Rechazada");

    private final String displayName;

    BookingRequestStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
