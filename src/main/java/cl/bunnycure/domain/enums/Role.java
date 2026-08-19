package cl.bunnycure.domain.enums;

/**
 * Roles del sistema BunnyCure para control de acceso RBAC.
 */
public enum Role {
    SUPER_ADMIN,
    SALON_ADMIN,
    ADMIN,          // Alias legacy compatible con SALON_ADMIN
    RECEPTIONIST,
    SPECIALIST,
    STAFF;          // Alias legacy compatible con SPECIALIST

    /**
     * Parsea una cadena de texto a un Role canónico de forma tolerante y segura.
     */
    public static Role fromString(String roleStr) {
        if (roleStr == null || roleStr.isBlank()) {
            return SALON_ADMIN;
        }
        String clean = roleStr.toUpperCase().replace("ROLE_", "").trim();
        return switch (clean) {
            case "SUPER_ADMIN" -> SUPER_ADMIN;
            case "SALON_ADMIN", "ADMIN", "OWNER" -> SALON_ADMIN;
            case "RECEPTIONIST", "RECEPCIONISTA" -> RECEPTIONIST;
            case "SPECIALIST", "STAFF", "MANICURISTA", "ESPECIALISTA" -> SPECIALIST;
            default -> SALON_ADMIN;
        };
    }

    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }

    public boolean isSalonAdmin() {
        return this == SALON_ADMIN || this == ADMIN || this == SUPER_ADMIN;
    }

    public boolean isReceptionist() {
        return this == RECEPTIONIST;
    }

    public boolean isSpecialist() {
        return this == SPECIALIST || this == STAFF;
    }
}
