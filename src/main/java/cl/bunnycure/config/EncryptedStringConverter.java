package cl.bunnycure.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Convertidor JPA para cifrado transparente en base de datos usando AES-256-GCM.
 * Protege datos sensibles en reposo (tokens de WhatsApp, contraseñas de SII, etc.).
 *
 * Formato cifrado: "ENC:" + Base64(IV [12 bytes] + CipherText [n bytes] + AuthTag [16 bytes]).
 * Mantiene compatibilidad hacia atrás: si el dato existente no tiene el prefijo "ENC:",
 * se lee en texto plano sin romper registros previos.
 */
@Slf4j
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String ENCRYPTED_PREFIX = "ENC:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static SecretKey secretKey;

    @Value("${app.security.encryption-key:${jwt.secret:BunnyCureSuperSecureMasterKey2026Encryption!}}")
    public void setSecretKey(String rawKey) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            secretKey = new SecretKeySpec(keyBytes, "AES");
            log.info("[ENCRYPTION] Clave de cifrado AES-256 inicializada correctamente.");
        } catch (Exception e) {
            log.error("[ENCRYPTION] Error inicializando clave de cifrado: {}", e.getMessage(), e);
            throw new IllegalStateException("No se pudo inicializar la clave de cifrado AES", e);
        }
    }

    private SecretKey getSecretKey() {
        if (secretKey == null) {
            try {
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                byte[] keyBytes = sha.digest("BunnyCureSuperSecureMasterKey2026Encryption!".getBytes(StandardCharsets.UTF_8));
                secretKey = new SecretKeySpec(keyBytes, "AES");
            } catch (Exception ignored) {
            }
        }
        return secretKey;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return attribute;
        }

        // Si ya está cifrado, no volver a cifrar
        if (attribute.startsWith(ENCRYPTED_PREFIX)) {
            return attribute;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), spec);

            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("[ENCRYPTION] Error cifrando valor en base de datos: {}", e.getMessage());
            throw new IllegalStateException("Error cifrando atributo sensible", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return dbData;
        }

        // Si no está cifrado (registro legado o migración en progreso), devolver tal cual
        if (!dbData.startsWith(ENCRYPTED_PREFIX)) {
            return dbData;
        }

        try {
            String base64Payload = dbData.substring(ENCRYPTED_PREFIX.length());
            byte[] decoded = Base64.getDecoder().decode(base64Payload);

            if (decoded.length < GCM_IV_LENGTH_BYTES) {
                log.warn("[ENCRYPTION] Payload cifrado inválido (longitud insuficiente)");
                return dbData;
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[ENCRYPTION] Error descifrando atributo desde base de datos: {}", e.getMessage());
            return null;
        }
    }
}
