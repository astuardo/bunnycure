package cl.bunnycure.config;

import cl.bunnycure.service.AppointmentService;
import cl.bunnycure.service.AppSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private static final String TWO_HOUR_LAST_RUN_AT_KEY = "reminder.two-hours.last-run-at";

    private final AppointmentService appointmentService;
    private final AppSettingsService appSettingsService;

    /**
     * Recordatorio para citas de mañana.
     * Solo activo cuando reminder.strategy = "day_before".
     * Se ejecuta con cron/zone configurables via properties.
     */
    @Scheduled(
            cron = "${bunnycure.reminder.day-before.cron:0 0 9 * * *}",
            zone = "${bunnycure.scheduler.timezone:America/Santiago}"
    )
    public void sendDayBeforeReminders() {
        if (!appSettingsService.isReminderDayBeforeEnabled()) {
            log.debug("[REMINDER-SCHEDULER] Recordatorio día anterior omitido (strategy={})",
                    appSettingsService.getReminderStrategy());
            return;
        }
        try {
            log.info("[REMINDER-SCHEDULER] Iniciando recordatorios día anterior...");
            appointmentService.sendRemindersForUpcomingAppointments();
            log.info("[REMINDER-SCHEDULER] Recordatorios día anterior completados");
        } catch (Exception e) {
            log.error("[REMINDER-SCHEDULER] Error en recordatorios día anterior", e);
        }
    }

    /**
     * Recordatorio para citas dentro de las próximas 2 horas.
     * Activo cuando reminder.strategy = "2hours" o "both" (default: "2hours").
     * Se ejecuta con cron/zone configurables via properties.
     * 
     * IMPORTANTE: Se ejecuta cada 30 minutos (no cada 2 horas) para detectar
     * citas agendadas recientemente. Ejemplo: si se agenda una cita a las 11:15
     * para las 12:00, el recordatorio se enviará en la siguiente ejecución 
     * (máximo 30 minutos de espera) en lugar de esperar hasta la próxima
     * ejecución de cada 2 horas.
     * 
     * El metodo verifica citas en ventana de 2h hacia adelante, pero se ejecuta
     * frecuentemente para no perder citas agendadas "last minute".
     */
    @Scheduled(cron = "0 * * * * *", zone = "${bunnycure.scheduler.timezone:America/Santiago}")
    public void sendTwoHourReminders() {
        if (!appSettingsService.isReminder2HoursEnabled()) {
            log.debug("[REMINDER-SCHEDULER] Recordatorio 2h omitido (strategy={})",
                    appSettingsService.getReminderStrategy());
            return;
        }

        int intervalMinutes = appSettingsService.getReminderTwoHoursIntervalMinutes();
        if (!shouldRunTwoHourReminderNow(intervalMinutes)) {
            log.debug("[REMINDER-SCHEDULER] Recordatorio 2h omitido por frecuencia ({} min)", intervalMinutes);
            return;
        }

        try {
            log.info("[REMINDER-SCHEDULER] Iniciando recordatorios 2h...");
            appointmentService.sendRemindersForAppointmentsIn2Hours();
            appSettingsService.set(TWO_HOUR_LAST_RUN_AT_KEY, Instant.now().toString());
            log.info("[REMINDER-SCHEDULER] Recordatorios 2h completados");
        } catch (Exception e) {
            log.error("[REMINDER-SCHEDULER] Error en recordatorios 2h", e);
        }
    }

    private boolean shouldRunTwoHourReminderNow(int intervalMinutes) {
        String lastRunRaw = appSettingsService.get(TWO_HOUR_LAST_RUN_AT_KEY, "");
        if (lastRunRaw == null || lastRunRaw.isBlank()) {
            return true;
        }

        try {
            Instant lastRun = Instant.parse(lastRunRaw);
            long elapsedMinutes = Duration.between(lastRun, Instant.now()).toMinutes();
            return elapsedMinutes >= intervalMinutes;
        } catch (Exception ex) {
            log.warn("[REMINDER-SCHEDULER] Valor inválido en {}='{}', se ejecutará ahora",
                    TWO_HOUR_LAST_RUN_AT_KEY, lastRunRaw);
            return true;
        }
    }
}
