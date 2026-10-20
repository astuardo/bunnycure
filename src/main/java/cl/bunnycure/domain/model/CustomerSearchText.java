package cl.bunnycure.domain.model;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Texto de búsqueda normalizado de una clienta: minúsculas, sin tildes y sin signos,
 * de modo que "maria", "María", "15007919" o "9 7000 0005" encuentren a la clienta
 * sin importar cómo se guardó su nombre, RUT o teléfono.
 *
 * <p>La migración V73 aplica la misma regla en SQL para las clientas existentes.</p>
 */
public final class CustomerSearchText {

    /** Cantidad de palabras que la consulta compara por separado (en cualquier orden). */
    public static final int MAX_TERMS = 4;

    private CustomerSearchText() {
    }

    public static String of(Customer customer) {
        return normalize(String.join(" ",
                nullToEmpty(customer.getFullName()),
                nullToEmpty(customer.getPhone()),
                nullToEmpty(customer.getRut()),
                nullToEmpty(customer.getEmail())));
    }

    /** Minúsculas, sin tildes, solo letras/dígitos/espacios y espacios simples. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Separa la búsqueda en exactamente {@link #MAX_TERMS} términos normalizados; los que sobran
     * se vacían ("" coincide con todo). Si hay más palabras, las últimas se buscan juntas en orden.
     */
    public static List<String> terms(String query) {
        String normalized = normalize(query);
        String[] words = normalized.isEmpty() ? new String[0] : normalized.split(" ");
        String[] terms = new String[MAX_TERMS];
        Arrays.fill(terms, "");
        for (int i = 0; i < words.length; i++) {
            int slot = Math.min(i, MAX_TERMS - 1);
            terms[slot] = terms[slot].isEmpty() ? words[i] : terms[slot] + "%" + words[i];
        }
        return List.of(terms);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
