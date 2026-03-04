package cl.bunnycure.config;

import cl.bunnycure.service.AppointmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReminderScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ReminderScheduler.class);

    private final AppointmentService appointmentService;

    public ReminderScheduler(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * Envía recordatorios para citas programadas para mañana
     * Se ejecuta diariamente a las 9:00 AM
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendDailyReminders() {
        try {
            logger.info("[REMINDER-SCHEDULER] Iniciando envío de recordatorios diarios...");
            appointmentService.sendRemindersForUpcomingAppointments();
            logger.info("[REMINDER-SCHEDULER] Recordatorios diarios enviados exitosamente");
        } catch (Exception e) {
            logger.error("[REMINDER-SCHEDULER] Error al enviar recordatorios diarios", e);
        }
    }

    /**
     * Envía recordatorios para citas en las próximas 2 horas
     * Se ejecuta cada 2 horas
     */
    @Scheduled(cron = "0 0 */2 * * *")
    public void sendTwoHourReminders() {
        try {
            logger.info("[REMINDER-SCHEDULER] Iniciando envío de recordatorios de 2 horas...");
            appointmentService.sendRemindersForAppointmentsIn2Hours();
            logger.info("[REMINDER-SCHEDULER] Recordatorios de 2 horas enviados exitosamente");
        } catch (Exception e) {
            logger.error("[REMINDER-SCHEDULER] Error al enviar recordatorios de 2 horas", e);
        }
    }
}
