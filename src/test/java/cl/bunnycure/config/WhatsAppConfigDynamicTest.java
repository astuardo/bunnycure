package cl.bunnycure.config;

import cl.bunnycure.service.AppSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WhatsAppConfig - Dynamic settings")
class WhatsAppConfigDynamicTest {

    @Mock
    private AppSettingsService appSettingsService;

    @Test
    @DisplayName("uses property fallback when AppSettingsService is not available")
    void usesPropertyFallbackWithoutAppSettingsService() {
        WhatsAppConfig config = new WhatsAppConfig();
        config.setCitaConfirmadaTemplateName("confirmacion_cita");

        assertEquals("confirmacion_cita", config.getCitaConfirmadaTemplateName());
    }

    @Test
    @DisplayName("uses app_settings value for template name when configured")
    void usesDynamicTemplateNameFromAppSettings() {
        WhatsAppConfig config = new WhatsAppConfig();
        config.setAppSettingsService(appSettingsService);
        config.setCitaConfirmadaTemplateName("confirmacion_cita");

        when(appSettingsService.get("whatsapp.template.confirmation.name", "confirmacion_cita"))
                .thenReturn("confirmacion_personalizada");

        assertEquals("confirmacion_personalizada", config.getCitaConfirmadaTemplateName());
    }

    @Test
    @DisplayName("uses app.name as top priority business name")
    void usesAppNameAsTopPriorityBusinessName() {
        WhatsAppConfig config = new WhatsAppConfig();
        config.setAppSettingsService(appSettingsService);
        config.setBusinessName("BunnyCure");

        when(appSettingsService.get("whatsapp.business.name", "BunnyCure"))
                .thenReturn("Marca WhatsApp");
        when(appSettingsService.get("app.name", "Marca WhatsApp"))
                .thenReturn("Marca Global");

        assertEquals("Marca Global", config.getBusinessName());
    }
}
