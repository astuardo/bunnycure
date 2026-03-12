package cl.bunnycure.service;

import cl.bunnycure.domain.model.AppSettings;
import cl.bunnycure.domain.repository.AppSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppSettingsServiceTest {

    @Mock
    private AppSettingsRepository repository;

    @InjectMocks
    private AppSettingsService appSettingsService;

    @Test
    void getHumanWhatsappNumber_UsesDedicatedSettingWhenPresent() {
        when(repository.findById("whatsapp.human.number"))
                .thenReturn(Optional.of(new AppSettings("whatsapp.human.number", "56988873031", null)));

        String result = appSettingsService.getHumanWhatsappNumber();

        assertEquals("56988873031", result);
    }

    @Test
    void getHumanWhatsappNumber_FallsBackToLegacyWhatsappNumber() {
        when(repository.findById("whatsapp.human.number")).thenReturn(Optional.empty());
        when(repository.findById("whatsapp.number"))
                .thenReturn(Optional.of(new AppSettings("whatsapp.number", "56977770000", null)));

        String result = appSettingsService.getHumanWhatsappNumber();

        assertEquals("56977770000", result);
    }

    @Test
    void isWhatsappHandoffEnabled_DefaultsToTrue() {
        when(repository.findById("whatsapp.handoff.enabled")).thenReturn(Optional.empty());

        assertTrue(appSettingsService.isWhatsappHandoffEnabled());
    }
}
