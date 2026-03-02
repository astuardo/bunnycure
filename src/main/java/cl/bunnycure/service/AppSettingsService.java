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
        return get("whatsapp.number", "56964499995");
    }

    public String getBookingMessageTemplate() {
        return get("booking.message.template",
                "Hola Bunny Cure! 🐰\nMe gustaría reservar una cita:\n• Servicio: {servicio}\n• Fecha: {fecha}\n• Bloque: {bloque}\n• Nombre: {nombre}\n• Teléfono: {telefono}\n¿Tienen disponibilidad?");
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
}
