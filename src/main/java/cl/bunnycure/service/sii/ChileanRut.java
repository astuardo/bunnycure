package cl.bunnycure.service.sii;

/** RUT chileno: normalización, dígito verificador (módulo 11) y validación de formato. */
public final class ChileanRut {

    private ChileanRut() {
    }

    public static String sanitizeRut(String rut) {
        if (rut == null)
            return "";
        String cleaned = rut.replaceAll("[.\\s]", "").toUpperCase();
        if (!cleaned.contains("-") && cleaned.length() > 1) {
            String number = cleaned.substring(0, cleaned.length() - 1);
            String dv = cleaned.substring(cleaned.length() - 1);
            cleaned = number + "-" + dv;
        }
        return cleaned;
    }

    public static boolean validateRutFormat(String rut) {
        if (rut == null || rut.isBlank()) {
            return false;
        }
        String sanitized = sanitizeRut(rut);
        if (!sanitized.matches("^\\d{7,9}-[0-9K]$")) {
            return false;
        }
        String[] parts = sanitized.split("-");
        if (parts.length != 2)
            return false;
        String number = parts[0];
        String expectedDv = parts[1];
        return calculateRutCheckDigit(number).equalsIgnoreCase(expectedDv);
    }

    public static String calculateRutCheckDigit(String rutNumberOnly) {
        String cleaned = rutNumberOnly.replaceAll("[^0-9]", "");
        if (cleaned.length() < 7) {
            return "";
        }
        int multiplier = 2;
        int sum = 0;
        for (int i = cleaned.length() - 1; i >= 0; i--) {
            sum += Integer.parseInt(String.valueOf(cleaned.charAt(i))) * multiplier;
            multiplier = (multiplier == 7) ? 2 : multiplier + 1;
        }
        int remainder = sum % 11;
        int checkDigit = 11 - remainder;
        if (checkDigit == 11)
            return "0";
        if (checkDigit == 10)
            return "K";
        return String.valueOf(checkDigit);
    }
}
