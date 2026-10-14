package cl.bunnycure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptedStringConverterTest {

    private EncryptedStringConverter converter;

    @BeforeEach
    void setUp() {
        converter = new EncryptedStringConverter();
        converter.setSecretKey("testSecretKeyForAesGcmEncryption2026!");
    }

    @Test
    void convertToDatabaseColumn_EncryptsPlaintextWithPrefix() {
        String plaintext = "EAABwzL1389TokenSecretMetaWhatsApp";
        String encrypted = converter.convertToDatabaseColumn(plaintext);

        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("ENC:"));
        assertNotEquals(plaintext, encrypted);
    }

    @Test
    void convertToEntityAttribute_DecryptsEncryptedStringSuccessfully() {
        String plaintext = "EAABwzL1389TokenSecretMetaWhatsApp";
        String encrypted = converter.convertToDatabaseColumn(plaintext);

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void convertToEntityAttribute_GracefulFallbackForUnencryptedLegacyPlaintext() {
        String legacyPlaintext = "unencrypted_legacy_token_12345";
        String decrypted = converter.convertToEntityAttribute(legacyPlaintext);

        assertEquals(legacyPlaintext, decrypted, "Debe retornar el texto plano original si no tiene prefijo ENC:");
    }

    @Test
    void nullAndBlankHandling() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertEquals("", converter.convertToDatabaseColumn(""));
        assertNull(converter.convertToEntityAttribute(null));
        assertEquals("", converter.convertToEntityAttribute(""));
    }
}
