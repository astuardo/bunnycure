package cl.bunnycure.service;

import cl.bunnycure.domain.model.AppSettings;
import cl.bunnycure.domain.repository.AppSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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

    // ── Reminder strategy ────────────────────────────────────────────────────

    @Test
    void getReminderStrategy_DefaultIs2hours() {
        when(repository.findById("reminder.strategy")).thenReturn(Optional.empty());

        assertEquals(AppSettingsService.REMINDER_STRATEGY_2HOURS, appSettingsService.getReminderStrategy());
    }

    @Test
    void isReminder2HoursEnabled_TrueWhen2hours() {
        when(repository.findById("reminder.strategy"))
                .thenReturn(Optional.of(new AppSettings("reminder.strategy", "2hours", null)));

        assertTrue(appSettingsService.isReminder2HoursEnabled());
        assertFalse(appSettingsService.isReminderMorningEnabled());
        assertFalse(appSettingsService.isReminderDayBeforeEnabled());
    }

    @Test
    void isReminderMorningEnabled_TrueWhenMorning() {
        when(repository.findById("reminder.strategy"))
                .thenReturn(Optional.of(new AppSettings("reminder.strategy", "morning", null)));

        assertTrue(appSettingsService.isReminderMorningEnabled());
        assertFalse(appSettingsService.isReminder2HoursEnabled());
        assertFalse(appSettingsService.isReminderDayBeforeEnabled());
    }

    @Test
    void isReminderMorningAndTwoHoursEnabled_WhenBoth() {
        when(repository.findById("reminder.strategy"))
                .thenReturn(Optional.of(new AppSettings("reminder.strategy", "both", null)));

        assertTrue(appSettingsService.isReminderMorningEnabled());
        assertTrue(appSettingsService.isReminder2HoursEnabled());
        assertFalse(appSettingsService.isReminderDayBeforeEnabled());
    }

    @Test
    void isReminderDayBeforeEnabled_TrueWhenDayBefore() {
        when(repository.findById("reminder.strategy"))
                .thenReturn(Optional.of(new AppSettings("reminder.strategy", "day_before", null)));

        assertTrue(appSettingsService.isReminderDayBeforeEnabled());
        assertFalse(appSettingsService.isReminderMorningEnabled());
        assertFalse(appSettingsService.isReminder2HoursEnabled());
    }

    @Test
    void getAppJavaLocale_UsesUnderscoreFormat() {
        when(repository.findById("app.locale"))
                .thenReturn(Optional.of(new AppSettings("app.locale", "es_CL", null)));

        Locale locale = appSettingsService.getAppJavaLocale();

        assertEquals("es", locale.getLanguage());
        assertEquals("CL", locale.getCountry());
    }

    @Test
    void getAppJavaLocale_UsesLanguageTagFormat() {
        when(repository.findById("app.locale"))
                .thenReturn(Optional.of(new AppSettings("app.locale", "en-US", null)));

        Locale locale = appSettingsService.getAppJavaLocale();

        assertEquals("en", locale.getLanguage());
        assertEquals("US", locale.getCountry());
    }

    @Test
    void getAppJavaLocale_FallsBackWhenInvalid() {
        when(repository.findById("app.locale"))
                .thenReturn(Optional.of(new AppSettings("app.locale", "", null)));

        Locale locale = appSettingsService.getAppJavaLocale();

        assertEquals("es", locale.getLanguage());
        assertEquals("CL", locale.getCountry());
    }

    // ── Field Modes (Fase 3) ──────────────────────────────────────────────────

    @Test
    void getFieldEmailMode_DefaultsToOptional() {
        when(repository.findById("field.email.mode")).thenReturn(Optional.empty());

        assertEquals(AppSettingsService.FIELD_MODE_OPTIONAL, appSettingsService.getFieldEmailMode());
    }

    @Test
    void getFieldGenderMode_DefaultsToOptional() {
        when(repository.findById("field.gender.mode")).thenReturn(Optional.empty());

        assertEquals(AppSettingsService.FIELD_MODE_OPTIONAL, appSettingsService.getFieldGenderMode());
    }

    @Test
    void getFieldBirthDateMode_DefaultsToOptional() {
        when(repository.findById("field.birth-date.mode")).thenReturn(Optional.empty());

        assertEquals(AppSettingsService.FIELD_MODE_OPTIONAL, appSettingsService.getFieldBirthDateMode());
    }

    @Test
    void getFieldEmergencyPhoneMode_DefaultsToHidden() {
        when(repository.findById("field.emergency-phone.mode")).thenReturn(Optional.empty());

        assertEquals(AppSettingsService.FIELD_MODE_HIDDEN, appSettingsService.getFieldEmergencyPhoneMode());
    }

    @Test
    void getFieldHealthNotesMode_DefaultsToHidden() {
        when(repository.findById("field.health-notes.mode")).thenReturn(Optional.empty());

        assertEquals(AppSettingsService.FIELD_MODE_HIDDEN, appSettingsService.getFieldHealthNotesMode());
    }

    @Test
    void getFieldGeneralNotesMode_DefaultsToOptional() {
        when(repository.findById("field.general-notes.mode")).thenReturn(Optional.empty());

        assertEquals(AppSettingsService.FIELD_MODE_OPTIONAL, appSettingsService.getFieldGeneralNotesMode());
    }

    @Test
    void isFieldVisible_ReturnsTrueForRequiredAndOptional() {
        assertTrue(appSettingsService.isFieldVisible(AppSettingsService.FIELD_MODE_REQUIRED));
        assertTrue(appSettingsService.isFieldVisible(AppSettingsService.FIELD_MODE_OPTIONAL));
    }

    @Test
    void isFieldVisible_ReturnsFalseForHidden() {
        assertFalse(appSettingsService.isFieldVisible(AppSettingsService.FIELD_MODE_HIDDEN));
    }

    @Test
    void isFieldRequired_ReturnsTrueOnlyForRequired() {
        assertTrue(appSettingsService.isFieldRequired(AppSettingsService.FIELD_MODE_REQUIRED));
        assertFalse(appSettingsService.isFieldRequired(AppSettingsService.FIELD_MODE_OPTIONAL));
        assertFalse(appSettingsService.isFieldRequired(AppSettingsService.FIELD_MODE_HIDDEN));
    }

    @Test
    void getFieldEmailMode_ReturnsConfiguredValue() {
        when(repository.findById("field.email.mode"))
                .thenReturn(Optional.of(new AppSettings("field.email.mode", "REQUIRED", null)));

        assertEquals(AppSettingsService.FIELD_MODE_REQUIRED, appSettingsService.getFieldEmailMode());
    }

    @Test
    void getFieldGenderMode_SupportsAllModes() {
        // REQUIRED
        when(repository.findById("field.gender.mode"))
                .thenReturn(Optional.of(new AppSettings("field.gender.mode", "REQUIRED", null)));
        assertEquals(AppSettingsService.FIELD_MODE_REQUIRED, appSettingsService.getFieldGenderMode());

        // HIDDEN
        when(repository.findById("field.gender.mode"))
                .thenReturn(Optional.of(new AppSettings("field.gender.mode", "HIDDEN", null)));
        assertEquals(AppSettingsService.FIELD_MODE_HIDDEN, appSettingsService.getFieldGenderMode());
    }
}
