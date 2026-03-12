package cl.bunnycure.service;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.repository.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AppointmentReminderService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderService.class);

    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;
    private final AppSettingsService appSettingsService;
    private final WhatsAppService whatsAppService;

    public AppointmentReminderService(AppointmentRepository appointmentRepository,
                                      NotificationService notificationService,
                                      AppSettingsService appSettingsService,
                                      WhatsAppService whatsAppService) {
        this.appointmentRepository = appointmentRepository;
        this.notificationService = notificationService;
        this.appSettingsService = appSettingsService;
        this.whatsAppService = whatsAppService;
    }

    /**
     * Se ejecuta diariamente a las 08:00 AM (zona America/Santiago).
     * Solo activo cuando reminder.strategy = "morning" o "both".
     * Envía recordatorios de citas para hoy a los clientes.
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "America/Santiago")
    @Transactional
    public void sendDailyReminders() {
        if (!appSettingsService.isReminderMorningEnabled()) {
            log.debug("[REMINDER] Recordatorio mañana omitido (strategy={})",
                    appSettingsService.getReminderStrategy());
            return;
        }

        log.info("[REMINDER] Iniciando envío de recordatorios diarios...");

        LocalDate today = LocalDate.now();
        List<Appointment> pendingReminders = appointmentRepository.findPendingRemindersForDateByStatuses(
                List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED),
                today
        );

        if (pendingReminders.isEmpty()) {
            log.info("[REMINDER] No hay citas pendientes para hoy");
            return;
        }

        log.info("[REMINDER] Se encontraron {} citas pendientes para recordar", pendingReminders.size());

        for (Appointment appointment : pendingReminders) {
            try {
                sendReminderForAppointment(appointment);
                // Marcar como recordatorio enviado
                appointment.setReminderSent(true);
                appointmentRepository.save(appointment);
                log.info("[REMINDER] ✅ Recordatorio enviado para cita ID: {}", appointment.getId());
            } catch (Exception e) {
                log.error("[REMINDER] ❌ Error enviando recordatorio para cita ID: {}", 
                        appointment.getId(), e);
            }
        }

        log.info("[REMINDER] Envío de recordatorios completado");
    }

    /**
     * Envía un recordatorio individual para una cita
     */
    private void sendReminderForAppointment(Appointment appointment) {
        log.info("[REMINDER] Procesando recordatorio para cita ID: {}", appointment.getId());
        
        String customerPhone = appointment.getCustomer().getPhone();
        String customerEmail = appointment.getCustomer().getEmail();
        String serviceName = appointment.getService().getName();
        String appointmentTime = appointment.getAppointmentTime() != null ? 
                appointment.getAppointmentTime().toString() : "";
        String appointmentDate = appointment.getAppointmentDate().toString();

        log.info("[REMINDER] Cliente: {}, Email: {}, Phone: {}", 
                appointment.getCustomer().getFullName(), customerEmail, customerPhone);

        // Enviar recordatorio por WhatsApp
        if (customerPhone != null && !customerPhone.isEmpty()) {
            sendWhatsAppReminder(appointment, customerPhone);
        } else {
            log.warn("[REMINDER] Sin teléfono para enviar WhatsApp");
        }

        // Enviar recordatorio por Email
        if (customerEmail != null && !customerEmail.isEmpty()) {
            sendEmailReminder(appointment, customerEmail, serviceName, appointmentTime, appointmentDate);
        } else {
            log.warn("[REMINDER] Sin email para enviar recordatorio");
        }
    }

    /**
     * Envía recordatorio por WhatsApp
     */
    private void sendWhatsAppReminder(Appointment appointment, String phone) {
        try {
            boolean whatsappEnabled = Boolean.parseBoolean(
                    appSettingsService.get("whatsapp.enabled", "true"));

            if (!whatsappEnabled) {
                log.debug("[REMINDER-WA] WhatsApp deshabilitado en configuración");
                return;
            }

            whatsAppService.sendRecordatorioCitaTemplate(appointment);
            log.info("[REMINDER-WA] ✅ Recordatorio WhatsApp enviado a {}", phone);

        } catch (Exception e) {
            log.error("[REMINDER-WA] Error enviando recordatorio WhatsApp", e);
        }
    }

    /**
     * Envía recordatorio por Email
     */
    private void sendEmailReminder(Appointment appointment, String email,
                                   String serviceName, String time, String date) {
        try {
            log.info("[REMINDER-EMAIL] Intentando enviar recordatorio a: {}", email);
            
            boolean emailEnabled = Boolean.parseBoolean(
                    appSettingsService.get("email.enabled", "true"));

            log.info("[REMINDER-EMAIL] Email habilitado: {}", emailEnabled);

            if (!emailEnabled) {
                log.warn("[REMINDER-EMAIL] Email deshabilitado en configuración");
                return;
            }

            log.info("[REMINDER-EMAIL] Llamando a notificationService.sendReminder...");
            
            notificationService.sendReminder(
                    email,
                    appointment.getCustomer().getFirstName(),
                    serviceName,
                    time,
                    date,
                    appointment
            );

            log.info("[REMINDER-EMAIL] ✅ Recordatorio enviado a {}", email);

        } catch (Exception e) {
            log.error("[REMINDER-EMAIL] ❌ Error enviando recordatorio por email a {}", email, e);
            throw new RuntimeException("Error enviando recordatorio: " + e.getMessage(), e);
        }
    }

    /**
     * Método manual para enviar recordatorios (útil para testing)
     */
    @Transactional
    public void sendManualReminder(Long appointmentId) {
        log.info("[REMINDER] Iniciando envío manual de recordatorio para cita ID: {}", appointmentId);
        
        var appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada"));

        log.info("[REMINDER] Cita encontrada: {} - Cliente: {}", 
                appointment.getId(), appointment.getCustomer().getFullName());

        sendReminderForAppointment(appointment);
        appointment.setReminderSent(true);
        appointmentRepository.save(appointment);

        log.info("[REMINDER] ✅ Recordatorio manual enviado exitosamente para cita ID: {}", appointmentId);
    }
}
