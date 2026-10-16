package cl.bunnycure.config;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversor de Logback que enmascara datos personales (emails y teléfonos) en todos los
 * mensajes de log antes de que salgan a consola / Logtail (Ley 21.719).
 * Se registra en logback-spring.xml reemplazando %m, %msg y %message.
 *
 * Ejemplos: "valentina@test.cl" -> "v***@test.cl", "+56977665544" -> "+*******5544".
 */
public class PiiMaskingConverter extends MessageConverter {

    private static final Pattern EMAIL = Pattern.compile(
            "([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,})");

    /**
     * Secuencias de 8 a 15 dígitos, con '+' y espacios opcionales. Los delimitadores excluyen
     * fechas (2026-10-16), horas, IPs, UUIDs y dígitos pegados a letras (WAMIDs, hashes).
     */
    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\w.:/+-])\\+?\\d(?: ?\\d){7,14}(?![\\w:/-]|\\.\\d)");

    private static final int VISIBLE_TRAILING_DIGITS = 4;

    @Override
    public String convert(ILoggingEvent event) {
        return mask(super.convert(event));
    }

    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String masked = EMAIL.matcher(message).replaceAll("$1***@$2");
        return maskPhones(masked);
    }

    private static String maskPhones(String message) {
        Matcher matcher = PHONE.matcher(message);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(maskDigits(matcher.group())));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String maskDigits(String phone) {
        long totalDigits = phone.chars().filter(Character::isDigit).count();
        long toHide = totalDigits - VISIBLE_TRAILING_DIGITS;
        StringBuilder sb = new StringBuilder(phone.length());
        for (char c : phone.toCharArray()) {
            if (Character.isDigit(c) && toHide > 0) {
                sb.append('*');
                toHide--;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
