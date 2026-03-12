package cl.bunnycure.service;

import cl.bunnycure.domain.model.AppSettings;
import cl.bunnycure.domain.repository.AppSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppSettingsService {

    private final AppSettingsRepository repository;

    public String get(String key, String defaultValue) {
        return repository.findById(key)
                .map(AppSettings::getValue)
                .orElse(defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return repository.findById(key)
                .map(s -> Boolean.parseBoolean(s.getValue()))
                .orElse(defaultValue);
    }

    @Transactional
    public void set(String key, String value) {
        var setting = repository.findById(key)
                .orElse(new AppSettings(key, value, null));
        setting.setValue(value);
        repository.save(setting);
    }

    @Transactional
    public void saveAll(Map<String, String> settings) {
        settings.forEach(this::set);
    }

    // ── Claves tipadas ────────────────────────────────────────────────────

    public boolean isBookingEnabled() {
        return getBoolean("booking.enabled", true);
    }

    public String getWhatsappNumber() {
        return getHumanWhatsappNumber();
    }

    public String getHumanWhatsappNumber() {
        String human = get("whatsapp.human.number", null);
        if (human != null && !human.isBlank()) {
            return human;
        }
        return get("whatsapp.number", "56988873031");
    }

    public String getHumanWhatsappDisplayName() {
        return get("whatsapp.human.display-name", "Equipo BunnyCure");
    }

    public boolean isWhatsappHandoffEnabled() {
        return getBoolean("whatsapp.handoff.enabled", true);
    }

    public String getWhatsappHandoffClientMessage() {
        return get("whatsapp.handoff.client-message",
                "Si necesitas ayuda personalizada, escríbenos al WhatsApp de atención humana: {numero}.");
    }

    public String getWhatsappHandoffAdminPrefill() {
        return get("whatsapp.handoff.admin-prefill",
                "Hola {nombre}, te escribe BunnyCure por tu solicitud o cita. Te contacto para ayudarte personalmente.");
    }

    public String getBookingMessageTemplate() {
        return get("booking.message.template",
                "Hola Bunny Cure! [conejo]\nMe gustaría reservar una cita:\n• Servicio: {servicio}\n• Fecha: {fecha}\n• Bloque: {bloque}\n• Nombre: {nombre}\n• Teléfono: {telefono}\n¿Tienen disponibilidad?");
    }

    public String getMorningBlock() {
        return get("booking.block.morning", "09:00 – 13:00");
    }

    public String getAfternoonBlock() {
        return get("booking.block.afternoon", "15:00 – 18:00");
    }

    public String getNightBlock() {
        return get("booking.block.night", "19:00 – 22:00");
    }

    public boolean isMorningEnabled()   { return getBoolean("booking.block.morning.enabled",   true); }
    public boolean isAfternoonEnabled() { return getBoolean("booking.block.afternoon.enabled", true); }
    public boolean isNightEnabled()     { return getBoolean("booking.block.night.enabled",     true); }

    // ── Estrategia de recordatorios ──────────────────────────────────────────

    /** Valores válidos para reminder.strategy */
    public static final String REMINDER_STRATEGY_2HOURS    = "2hours";
    public static final String REMINDER_STRATEGY_MORNING   = "morning";
    public static final String REMINDER_STRATEGY_DAY_BEFORE = "day_before";
    public static final String REMINDER_STRATEGY_BOTH      = "both";

    /**
     * Estrategia de recordatorios configurada.
     * Default: "2hours" (solo avisa 2h antes de la cita).
     */
    public String getReminderStrategy() {
        return get("reminder.strategy", REMINDER_STRATEGY_2HOURS);
    }

    /** true cuando la estrategia activa el recordatorio del día de la cita a las 08:00 */
    public boolean isReminderMorningEnabled() {
        String s = getReminderStrategy();
        return REMINDER_STRATEGY_MORNING.equals(s) || REMINDER_STRATEGY_BOTH.equals(s);
    }

    /** true cuando la estrategia activa el recordatorio 2h antes de la cita */
    public boolean isReminder2HoursEnabled() {
        String s = getReminderStrategy();
        return REMINDER_STRATEGY_2HOURS.equals(s) || REMINDER_STRATEGY_BOTH.equals(s);
    }

    /** true cuando la estrategia activa el recordatorio del día anterior a las 09:00 */
    public boolean isReminderDayBeforeEnabled() {
        return REMINDER_STRATEGY_DAY_BEFORE.equals(getReminderStrategy());
    }
}
