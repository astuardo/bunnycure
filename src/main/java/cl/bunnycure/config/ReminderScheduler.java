package cl.bunnycure.config;

import cl.bunnycure.service.AppointmentService;
import cl.bunnycure.service.AppSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final AppointmentService appointmentService;
    private final AppSettingsService appSettingsService;

    /**
     * Recordatorio para citas de mañana.
     * Solo activo cuando reminder.strategy = "day_before".
     * Se ejecuta diariamente a las 09:00 (zona America/Santiago).
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "America/Santiago")
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
     * Se ejecuta cada 2 horas (zona America/Santiago).
     */
    @Scheduled(cron = "0 0 */2 * * *", zone = "America/Santiago")
    public void sendTwoHourReminders() {
        if (!appSettingsService.isReminder2HoursEnabled()) {
            log.debug("[REMINDER-SCHEDULER] Recordatorio 2h omitido (strategy={})",
                    appSettingsService.getReminderStrategy());
            return;
        }
        try {
            log.info("[REMINDER-SCHEDULER] Iniciando recordatorios 2h...");
            appointmentService.sendRemindersForAppointmentsIn2Hours();
            log.info("[REMINDER-SCHEDULER] Recordatorios 2h completados");
        } catch (Exception e) {
            log.error("[REMINDER-SCHEDULER] Error en recordatorios 2h", e);
        }
    }
}
