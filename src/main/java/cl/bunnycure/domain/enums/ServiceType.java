package cl.bunnycure.domain.enums;

import lombok.Getter;

@Getter
public enum ServiceType {

    MANICURE_BRILLO("Manicure + Brillo"),
    MANICURE_SEMI("Manicure Semi-Permanente"),
    MANICURE_MEN("Manicure Men"),
    KAPPING_GEL("Kapping Gel"),
    SOFT_GEL("Soft Gel"),
    POLYGEL("Polygel Esculpido"),
    PEDICURE_ESMALTADO("Pedicure + Esmaltado"),
    PEDICURE_SPA("Pedicure + Esmaltado + Spa"),
    NAIL_ART("Nail Art"),
    RETIRO("Retiro de Esmalte");

    private final String displayName;

    ServiceType(String displayName) {
        this.displayName = displayName;
    }

}