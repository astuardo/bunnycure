package cl.bunnycure.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import cl.bunnycure.domain.model.AppSettings;
import cl.bunnycure.domain.repository.AppSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

/**
 * Tests para AppSettingsService — Fase 2 (Parametrización de Identidad & Branding)
 *
 * ✅ Task: T1.2 (SPRINT_1_QUICKSTART.md)
 * Estos tests validan que los 10 getters de branding funcionan correctamente
 * y que los defaults se aplican cuando no hay valores en BD.
 *
 * Usa Mockito para evitar dependencias de BD/Spring, permitiendo tests más rápidos
 * y aislados.
 *
 * @author GitHub Copilot
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AppSettingsService - Phase 2 Branding Tests")
class AppSettingsServicePhase2Test {

    @Mock
    private AppSettingsRepository repository;

    @InjectMocks
    private AppSettingsService appSettingsService;

    @BeforeEach
    void setUp() {
        // Mockito inyecta automáticamente @InjectMocks
        // Cada test comienza con mocks limpios
    }

    // ────────────────────────────────────────────────────────────────────
    // TESTS PARA LOS 10 GETTERS DE BRANDING (Fase 1)
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAppName should return BunnyCure by default")
    void testGetAppName() {
        // Arrange
        when(repository.findById("app.name")).thenReturn(Optional.empty());

        // Act
        String actual = appSettingsService.getAppName();

        // Assert
        assertNotNull(actual, "App name should not be null");
        assertEquals("BunnyCure", actual, "App name should default to BunnyCure");
        verify(repository, times(1)).findById("app.name");
    }

    @Test
    @DisplayName("getAppSlogan should return default slogan with emoji")
    void testGetAppSlogan() {
        // Arrange
        when(repository.findById("app.slogan")).thenReturn(Optional.empty());

        // Act
        String actual = appSettingsService.getAppSlogan();

        // Assert
        assertNotNull(actual, "Slogan should not be null");
        assertEquals("Arte en tus manos ✨", actual, "Slogan should match default");
        assertTrue(actual.contains("✨"), "Slogan should contain emoji");
    }

    @Test
    @DisplayName("getAppEmail should return valid email address")
    void testGetAppEmail() {
        // Arrange
        when(repository.findById("app.email")).thenReturn(Optional.empty());

        // Act
        String actual = appSettingsService.getAppEmail();

        // Assert
        assertNotNull(actual, "Email should not be null");
        assertTrue(actual.contains("@"), "Email should contain @");
        assertTrue(actual.contains("."), "Email should contain domain");
        assertEquals("contacto@bunnycure.cl", actual, "Email should match default");
    }

    @Test
    @DisplayName("getAppLogoUrl should return valid URL path")
    void testGetAppLogoUrl() {
        // Arrange
        when(repository.findById("app.logo-url")).thenReturn(Optional.empty());

        // Act
        String actual = appSettingsService.getAppLogoUrl();

        // Assert
        assertNotNull(actual, "Logo URL should not be null");
        assertTrue(
            actual.startsWith("/") || actual.startsWith("http"),
            "Logo URL should start with / (relative) or http (absolute)"
        );
    }

    @Test
    @DisplayName("getAppPrimaryColor should return valid HEX color")
    void testGetAppPrimaryColor() {
        // Arrange
        when(repository.findById("app.primary-color")).thenReturn(Optional.empty());
        String hexColorRegex = "^#[0-9A-Fa-f]{6}$";

        // Act
        String actual = appSettingsService.getAppPrimaryColor();

        // Assert
        assertNotNull(actual, "Primary color should not be null");
        assertTrue(actual.matches(hexColorRegex), 
            "Primary color should be valid HEX format (#RRGGBB), got: " + actual);
        assertEquals("#F472B6", actual, "Primary color should be default BunnyCure pink");
    }

    @Test
    @DisplayName("getAppSecondaryColor should return valid HEX color")
    void testGetAppSecondaryColor() {
        // Arrange
        when(repository.findById("app.secondary-color")).thenReturn(Optional.empty());
        String hexColorRegex = "^#[0-9A-Fa-f]{6}$";

        // Act
        String actual = appSettingsService.getAppSecondaryColor();

        // Assert
        assertNotNull(actual, "Secondary color should not be null");
        assertTrue(actual.matches(hexColorRegex),
            "Secondary color should be valid HEX format (#RRGGBB), got: " + actual);
        assertEquals("#8B5CF6", actual, "Secondary color should be default BunnyCure purple");
    }

    @Test
    @DisplayName("getAppTimezone should return valid IANA timezone")
    void testGetAppTimezone() {
        // Arrange
        when(repository.findById("app.timezone")).thenReturn(Optional.empty());

        // Act
        String actual = appSettingsService.getAppTimezone();

        // Assert
        assertNotNull(actual, "Timezone should not be null");
        assertDoesNotThrow(
            () -> java.time.ZoneId.of(actual),
            "Timezone should be valid IANA format (e.g., 'America/Santiago'), got: " + actual
        );
        assertEquals("America/Santiago", actual, "Timezone should default to Chile");
    }

    @Test
    @DisplayName("getAppLocale should return valid locale format (xx_YY)")
    void testGetAppLocale() {
        // Arrange
        when(repository.findById("app.locale")).thenReturn(Optional.empty());
        String localeRegex = "^[a-z]{2}_[A-Z]{2}$";

        // Act
        String actual = appSettingsService.getAppLocale();

        // Assert
        assertNotNull(actual, "Locale should not be null");
        assertTrue(actual.matches(localeRegex),
            "Locale should be in format xx_YY (e.g., 'es_CL'), got: " + actual);
        assertEquals("es_CL", actual, "Locale should default to es_CL (Spanish/Chile)");
    }

    @Test
    @DisplayName("getAppCurrency should return valid ISO 4217 currency code")
    void testGetAppCurrency() {
        // Arrange
        when(repository.findById("app.currency")).thenReturn(Optional.empty());

        // Act
        String actual = appSettingsService.getAppCurrency();

        // Assert
        assertNotNull(actual, "Currency should not be null");
        assertEquals(3, actual.length(), 
            "Currency code should be 3 characters (ISO 4217), got: " + actual);
        assertEquals("CLP", actual, "Currency should default to CLP (Chilean Peso)");
    }

    @Test
    @DisplayName("getAppServiceTip should return non-empty service advice")
    void testGetAppServiceTip() {
        // Arrange
        when(repository.findById("app.service-tip")).thenReturn(Optional.empty());

        // Act
        String actual = appSettingsService.getAppServiceTip();

        // Assert
        assertNotNull(actual, "Service tip should not be null");
        assertFalse(actual.isEmpty(), "Service tip should not be empty");
        assertTrue(actual.length() > 5, "Service tip should have meaningful content");
        // Default is specific to nail art
        assertTrue(actual.contains("uñas"), "Default service tip should mention nails");
    }

    // ────────────────────────────────────────────────────────────────────
    // TESTS PARA FALLBACK A DEFAULTS (validar que el patrón funciona)
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("get() with non-existent key should return provided default value")
    void testFallbackToDefault() {
        // Arrange
        String nonExistentKey = "nonexistent.key.that.does.not.exist.12345";
        String expectedDefault = "DEFAULT_VALUE";
        when(repository.findById(nonExistentKey)).thenReturn(Optional.empty());

        // Act
        String actual = appSettingsService.get(nonExistentKey, expectedDefault);

        // Assert
        assertEquals(expectedDefault, actual,
            "Should return default value when key doesn't exist in database");
    }

    // ────────────────────────────────────────────────────────────────────
    // TESTS PARA COMPATIBILIDAD DE TIPOS
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("All 10 branding getters should be non-null and non-empty")
    void testAllBrandingGettersReturnValues() {
        // Arrange - mock all keys as non-existent to get defaults
        when(repository.findById(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        String appName = appSettingsService.getAppName();
        String appSlogan = appSettingsService.getAppSlogan();
        String appEmail = appSettingsService.getAppEmail();
        String appLogoUrl = appSettingsService.getAppLogoUrl();
        String primaryColor = appSettingsService.getAppPrimaryColor();
        String secondaryColor = appSettingsService.getAppSecondaryColor();
        String timezone = appSettingsService.getAppTimezone();
        String locale = appSettingsService.getAppLocale();
        String currency = appSettingsService.getAppCurrency();
        String serviceTip = appSettingsService.getAppServiceTip();

        // Verify none are null
        assertNotNull(appName, "App name must not be null");
        assertNotNull(appSlogan, "App slogan must not be null");
        assertNotNull(appEmail, "App email must not be null");
        assertNotNull(appLogoUrl, "App logo URL must not be null");
        assertNotNull(primaryColor, "Primary color must not be null");
        assertNotNull(secondaryColor, "Secondary color must not be null");
        assertNotNull(timezone, "Timezone must not be null");
        assertNotNull(locale, "Locale must not be null");
        assertNotNull(currency, "Currency must not be null");
        assertNotNull(serviceTip, "Service tip must not be null");

        // Verify none are empty
        assertFalse(appName.isEmpty(), "App name must not be empty");
        assertFalse(appSlogan.isEmpty(), "App slogan must not be empty");
        assertFalse(appEmail.isEmpty(), "App email must not be empty");
        assertFalse(appLogoUrl.isEmpty(), "App logo URL must not be empty");
        assertFalse(primaryColor.isEmpty(), "Primary color must not be empty");
        assertFalse(secondaryColor.isEmpty(), "Secondary color must not be empty");
        assertFalse(timezone.isEmpty(), "Timezone must not be empty");
        assertFalse(locale.isEmpty(), "Locale must not be empty");
        assertFalse(currency.isEmpty(), "Currency must not be empty");
        assertFalse(serviceTip.isEmpty(), "Service tip must not be empty");
    }

    @Test
    @DisplayName("Both HEX colors should be different from each other")
    void testPrimaryAndSecondaryColorsDifferent() {
        // Arrange
        when(repository.findById(anyString())).thenReturn(Optional.empty());

        // Act
        String primary = appSettingsService.getAppPrimaryColor();
        String secondary = appSettingsService.getAppSecondaryColor();

        // Assert
        assertNotEquals(primary, secondary,
            "Primary and secondary colors should be different for visual contrast");
    }
}
