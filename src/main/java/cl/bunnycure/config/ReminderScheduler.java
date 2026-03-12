package cl.bunnycure.config;

import cl.bunnycure.service.AppointmentService;
import cl.bunnycure.service.AppSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReminderScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ReminderScheduler.class);

    private final AppointmentService appointmentService;
    private final AppSettingsService appSettingsService;

    public ReminderScheduler(AppointmentService appointmentService,
                             AppSettingsService appSettingsService) {
        this.appointmentService = appointmentService;
        this.appSettingsService = appSettingsService;
    }

    /**
     * Recordatorio para citas de mañana.
     * Solo activo cuando reminder.strategy = "day_before".
     * Se ejecuta diariamente a las 09:00 (zona America/Santiago).
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "America/Santiago")
    public void sendDayBeforeReminders() {
        if (!appSettingsService.isReminderDayBeforeEnabled()) {
            logger.debug("[REMINDER-SCHEDULER] Recordatorio día anterior omitido (strategy={})",
                    appSettingsService.getReminderStrategy());
            return;
        }
        try {
            logger.info("[REMINDER-SCHEDULER] Iniciando recordatorios día anterior...");
            appointmentService.sendRemindersForUpcomingAppointments();
            logger.info("[REMINDER-SCHEDULER] Recordatorios día anterior completados");
        } catch (Exception e) {
            logger.error("[REMINDER-SCHEDULER] Error en recordatorios día anterior", e);
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
            logger.debug("[REMINDER-SCHEDULER] Recordatorio 2h omitido (strategy={})",
                    appSettingsService.getReminderStrategy());
            return;
        }
        try {
            logger.info("[REMINDER-SCHEDULER] Iniciando recordatorios 2h...");
            appointmentService.sendRemindersForAppointmentsIn2Hours();
            logger.info("[REMINDER-SCHEDULER] Recordatorios 2h completados");
        } catch (Exception e) {
            logger.error("[REMINDER-SCHEDULER] Error en recordatorios 2h", e);
        }
    }
}
