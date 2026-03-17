package cl.bunnycure.web.controller;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.service.AppointmentService;
import cl.bunnycure.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/admin/appointments")
@RequiredArgsConstructor
public class AdminAppointmentController {

    private final AppointmentService appointmentService;
    private final NotificationService notificationService;

    /**
     * Muestra la página de recordatorios con citas pendientes
     */
    @GetMapping("/reminders")
    public String showReminders(Model model) {
        try {
            // Obtener citas confirmadas
            List<Appointment> appointments = appointmentService.findByStatus(AppointmentStatus.CONFIRMED);
            
            // Filtrar solo citas futuras
            LocalDate today = LocalDate.now();
            List<Appointment> upcomingAppointments = appointments.stream()
                    .filter(a -> a.getAppointmentDate().isAfter(today) || 
                               (a.getAppointmentDate().isEqual(today) && !a.isReminderSent()))
                    .toList();

            model.addAttribute("appointments", upcomingAppointments);
            model.addAttribute("totalPending", upcomingAppointments.size());
            
            log.info("[ADMIN] Mostrando página de recordatorios con {} citas pendientes", upcomingAppointments.size());
        } catch (Exception e) {
            log.error("[ADMIN] Error al obtener citas para recordatorios", e);
            model.addAttribute("error", "Error al cargar recordatorios");
        }
        return "admin/appointments/reminders";
    }

    /**
     * Envía un recordatorio individual para una cita
     */
    @PostMapping("/{id}/send-reminder")
    public String sendReminder(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Appointment appointment = appointmentService.getAppointmentById(id)
                    .orElseThrow(() -> new RuntimeException("Cita no encontrada"));

            notificationService.sendReminderNotification(appointment, "custom");

            // Marcar como enviado
            appointment.setReminderSent(true);
            appointmentService.saveAppointment(appointment);

            log.info("[ADMIN] Recordatorio enviado para cita ID: {}", id);
            redirectAttributes.addFlashAttribute("success", "Recordatorio enviado exitosamente");
        } catch (Exception e) {
            log.error("[ADMIN] Error al enviar recordatorio para cita ID: {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Error al enviar recordatorio");
        }
        return "redirect:/admin/appointments/reminders";
    }

    /**
     * Envía recordatorios en lote para citas de mañana
     */
    @PostMapping("/send-reminders-tomorrow")
    public String sendRemindersTomorrow(RedirectAttributes redirectAttributes) {
        try {
            log.info("[ADMIN] Enviando recordatorios para citas de mañana...");
            appointmentService.sendRemindersForUpcomingAppointments();
            redirectAttributes.addFlashAttribute("success", "Recordatorios para mañana enviados exitosamente");
        } catch (Exception e) {
            log.error("[ADMIN] Error al enviar recordatorios para mañana", e);
            redirectAttributes.addFlashAttribute("error", "Error al enviar recordatorios");
        }
        return "redirect:/admin/appointments/reminders";
    }

    /**
     * Envía recordatorios en lote para citas en las próximas 2 horas
     */
    @PostMapping("/send-reminders-2hours")
    public String sendReminders2Hours(RedirectAttributes redirectAttributes) {
        try {
            log.info("[ADMIN] Enviando recordatorios para citas en 2 horas...");
            appointmentService.sendRemindersForAppointmentsIn2Hours();
            redirectAttributes.addFlashAttribute("success", "Recordatorios urgentes enviados exitosamente");
        } catch (Exception e) {
            log.error("[ADMIN] Error al enviar recordatorios de 2 horas", e);
            redirectAttributes.addFlashAttribute("error", "Error al enviar recordatorios");
        }
        return "redirect:/admin/appointments/reminders";
    }
}
