package cl.bunnycure.service;

import cl.bunnycure.domain.model.AppSettings;
import cl.bunnycure.domain.repository.AppSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
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

    public String getAdminAlertWhatsappNumber(String fallbackValue) {
        String configured = get("whatsapp.admin-alert.number", null);
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return fallbackValue;
    }

    public boolean isWhatsappAdminAlertEnabled() {
        return isWhatsappAdminAlertEnabled(true);
    }

    public boolean isWhatsappAdminAlertEnabled(boolean defaultValue) {
        return getBoolean("whatsapp.admin-alert.enabled", defaultValue);
    }

    public boolean isMailEnabled() {
        return isMailEnabled(true);
    }

    public boolean isMailEnabled(boolean defaultValue) {
        return getBoolean("mail.enabled", defaultValue);
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

    // ── Identidad & Branding (Fase 1) ───────────────────────────────────────

    /** Nombre del negocio. Default: "BunnyCure" */
    public String getAppName() {
        return get("app.name", "BunnyCure");
    }

    /** Eslogan del negocio. Default: "Arte en tus manos ✨" */
    public String getAppSlogan() {
        return get("app.slogan", "Arte en tus manos ✨");
    }

    /** Email del negocio. Default: "contacto@bunnycure.cl" */
    public String getAppEmail() {
        return get("app.email", "contacto@bunnycure.cl");
    }

    /** URL del logo del negocio */
    public String getAppLogoUrl() {
        return get("app.logo-url", "/images/logo.png");
    }

    /** Color primario en formato HEX. Default: "#F472B6" (rosa BunnyCure) */
    public String getAppPrimaryColor() {
        return get("app.primary-color", "#F472B6");
    }

    /** Color secundario en formato HEX. Default: "#8B5CF6" (púrpura BunnyCure) */
    public String getAppSecondaryColor() {
        return get("app.secondary-color", "#8B5CF6");
    }

    /** Zona horaria del negocio. Default: "America/Santiago" */
    public String getAppTimezone() {
        return get("app.timezone", "America/Santiago");
    }

    /** Locale del negocio (ej: "es_CL"). Default: "es_CL" */
    public String getAppLocale() {
        return get("app.locale", "es_CL");
    }

    /**
     * Locale del negocio parseado para uso en Java.
     * Acepta formatos "es_CL", "es-CL" y language tags BCP-47.
     */
    public Locale getAppJavaLocale() {
        String configured = getAppLocale();
        if (configured == null || configured.isBlank()) {
            return new Locale("es", "CL");
        }

        String normalized = configured.trim().replace('-', '_');

        try {
            if (normalized.contains("_")) {
                String[] parts = normalized.split("_", 3);
                if (parts.length >= 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                    return new Locale(parts[0].toLowerCase(Locale.ROOT), parts[1].toUpperCase(Locale.ROOT));
                }
            }

            Locale fromTag = Locale.forLanguageTag(configured.replace('_', '-'));
            if (fromTag.getLanguage() != null && !fromTag.getLanguage().isBlank()) {
                return fromTag;
            }
        } catch (Exception ignored) {
            // Keep fallback below when locale format is invalid.
        }

        return new Locale("es", "CL");
    }

    /** Moneda del negocio (ej: "CLP"). Default: "CLP" */
    public String getAppCurrency() {
        return get("app.currency", "CLP");
    }

    /** Consejo personalizado para el servicio del negocio (se usa en emails) */
    public String getAppServiceTip() {
        return get("app.service-tip", "Llega con las uñas limpias y sin esmalte");
    }

    // ── Contacto & Redes Sociales (Fase 2) ───────────────────────────────────

    /** URL del sitio web principal. Default: "https://www.bunnycure.cl" */
    public String getAppWebsiteUrl() {
        return get("app.website.url", "https://www.bunnycure.cl");
    }

    /** URL de Instagram. Default: "https://www.instagram.com/bunny.cure" */
    public String getAppInstagramUrl() {
        return get("app.instagram.url", "https://www.instagram.com/bunny.cure");
    }

    /** Handle de Instagram. Default: "@bunny.cure" */
    public String getAppInstagramHandle() {
        return get("app.instagram.handle", "@bunny.cure");
    }

    /** Teléfono para display (humano). Default: "+56 9 6449 9995" */
    public String getAppPhoneDisplay() {
        return get("app.phone.display", "+56 9 6449 9995");
    }

    // ── Configuración de Campos Dinámicos (Fase 3) ─────────────────────────

    /** Valores válidos para field mode */
    public static final String FIELD_MODE_REQUIRED = "REQUIRED";
    public static final String FIELD_MODE_OPTIONAL = "OPTIONAL";
    public static final String FIELD_MODE_HIDDEN = "HIDDEN";

    /** Modo del campo email. Default: OPTIONAL */
    public String getFieldEmailMode() {
        return get("field.email.mode", FIELD_MODE_OPTIONAL);
    }

    /** Modo del campo género. Default: OPTIONAL */
    public String getFieldGenderMode() {
        return get("field.gender.mode", FIELD_MODE_OPTIONAL);
    }

    /** Modo del campo fecha de nacimiento. Default: OPTIONAL */
    public String getFieldBirthDateMode() {
        return get("field.birth-date.mode", FIELD_MODE_OPTIONAL);
    }

    /** Modo del campo teléfono de emergencia. Default: HIDDEN */
    public String getFieldEmergencyPhoneMode() {
        return get("field.emergency-phone.mode", FIELD_MODE_HIDDEN);
    }

    /** Modo del campo notas de salud. Default: HIDDEN */
    public String getFieldHealthNotesMode() {
        return get("field.health-notes.mode", FIELD_MODE_HIDDEN);
    }

    /** Modo del campo notas generales. Default: OPTIONAL */
    public String getFieldGeneralNotesMode() {
        return get("field.general-notes.mode", FIELD_MODE_OPTIONAL);
    }

    /** Verifica si un campo está visible (no HIDDEN) */
    public boolean isFieldVisible(String fieldMode) {
        return !FIELD_MODE_HIDDEN.equals(fieldMode);
    }

    /** Verifica si un campo es requerido */
    public boolean isFieldRequired(String fieldMode) {
        return FIELD_MODE_REQUIRED.equals(fieldMode);
    }

    // ── Templates de Notificaciones PWA (Fase 4) ────────────────────────────

    /** Título por defecto para notificaciones. Default: "Recordatorio de Cita" */
    public String getNotificationDefaultTitle() {
        return get("notification.template.default.title", "Recordatorio de Cita");
    }

    /** Cuerpo por defecto para notificaciones. Default con variables */
    public String getNotificationDefaultBody() {
        return get("notification.template.default.body",
                "Hola {customerName}, tienes una cita de {serviceName} el {date} a las {time}.");
    }

    /** Título para notificaciones 2h antes. Default: "¡Tu cita es pronto!" */
    public String getNotificationTwoHourTitle() {
        return get("notification.template.2hour.title", "¡Tu cita es pronto!");
    }

    /** Cuerpo para notificaciones 2h antes. Default con variables */
    public String getNotificationTwoHourBody() {
        return get("notification.template.2hour.body",
                "Hola {customerName}, tu cita de {serviceName} es en {minutesUntil} minutos ({time}). ¡Te esperamos!");
    }
}
