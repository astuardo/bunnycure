package cl.bunnycure.service;

import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.BookingRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Ejemplos de cómo integrar WhatsAppService con los servicios existentes.
 * 
 * NOTA: Esta clase es solo de referencia y no debe ser usada directamente.
 * Los ejemplos muestran cómo integrar WhatsApp en los servicios existentes.
 */
@Component
@RequiredArgsConstructor
public class WhatsAppIntegrationExamples {

    private final WhatsAppService whatsAppService;
    private final NotificationService notificationService;

    /**
     * EJEMPLO 1: Enviar confirmación de cita por email Y WhatsApp
     * 
     * Modifica BookingRequestService.approve() línea ~148:
     * 
     * Cambia:
     *   notificationService.sendAppointmentConfirmation(savedAppointment);
     * 
     * Por:
     *   // Notificar por email
     *   notificationService.sendAppointmentConfirmation(savedAppointment);
     *   // Notificar por WhatsApp
     *   whatsAppService.sendAppointmentConfirmation(savedAppointment);
     */
    public void ejemploConfirmacionCita(Appointment appointment) {
        // Email
        notificationService.sendAppointmentConfirmation(appointment);
        // WhatsApp
        whatsAppService.sendAppointmentConfirmation(appointment);
    }

    /**
     * EJEMPLO 2: Enviar recepción de solicitud por email Y WhatsApp
     * 
     * Modifica BookingRequestService.create() línea ~82:
     * 
     * Cambia:
     *   if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
     *       notificationService.sendBookingRequestReceived(saved);
     *   }
     * 
     * Por:
     *   // Notificar por email (si tiene)
     *   if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
     *       notificationService.sendBookingRequestReceived(saved);
     *   }
     *   // Notificar por WhatsApp (si tiene teléfono)
     *   if (saved.getPhone() != null && !saved.getPhone().isBlank()) {
     *       whatsAppService.sendBookingRequestReceived(saved);
     *   }
     */
    public void ejemploRecepcionSolicitud(BookingRequest request) {
        // Email
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            notificationService.sendBookingRequestReceived(request);
        }
        // WhatsApp
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            whatsAppService.sendBookingRequestReceived(request);
        }
    }

    /**
     * EJEMPLO 3: Enviar rechazo de solicitud por email Y WhatsApp
     * 
     * Modifica BookingRequestService.reject() línea ~166:
     * 
     * Cambia:
     *   if (request.getEmail() != null && !request.getEmail().isBlank()) {
     *       notificationService.sendBookingRequestRejected(request);
     *   }
     * 
     * Por:
     *   // Notificar por email (si tiene)
     *   if (request.getEmail() != null && !request.getEmail().isBlank()) {
     *       notificationService.sendBookingRequestRejected(request);
     *   }
     *   // Notificar por WhatsApp (si tiene teléfono)
     *   if (request.getPhone() != null && !request.getPhone().isBlank()) {
     *       whatsAppService.sendBookingRequestRejected(request);
     *   }
     */
    public void ejemploRechazoSolicitud(BookingRequest request) {
        // Email
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            notificationService.sendBookingRequestRejected(request);
        }
        // WhatsApp
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            whatsAppService.sendBookingRequestRejected(request);
        }
    }

    /**
     * EJEMPLO 4: Enviar recordatorio de cita por email Y WhatsApp
     * 
     * Modifica AppointmentReminderService líneas correspondientes:
     * 
     * Agrega después de enviar email:
     *   whatsAppService.sendAppointmentReminder(appointment);
     */
    public void ejemploRecordatorioCita(Appointment appointment) {
        // Email (lógica existente en AppointmentReminderService)
        notificationService.sendCancellationNotice(appointment); // o el método de recordatorio
        // WhatsApp
        whatsAppService.sendAppointmentReminder(appointment);
    }

    /**
     * EJEMPLO 5: Enviar cancelación de cita por email Y WhatsApp
     * 
     * Modifica AppointmentService cuando se cancela una cita:
     * 
     * Agrega:
     *   notificationService.sendCancellationNotice(appointment);
     *   whatsAppService.sendAppointmentCancellation(appointment);
     */
    public void ejemploCancelacionCita(Appointment appointment) {
        // Email
        notificationService.sendCancellationNotice(appointment);
        // WhatsApp
        whatsAppService.sendAppointmentCancellation(appointment);
    }

    /**
     * EJEMPLO 6: Enviar mensaje de texto personalizado
     * 
     * Útil para casos especiales o notificaciones administrativas
     */
    public void ejemploMensajePersonalizado() {
        String telefono = "+56912345678";
        String mensaje = "Hola! Te recordamos que mañana tenemos horarios disponibles. " +
                        "Agenda tu cita en www.bunnycure.cl 🐇✨";
        
        whatsAppService.sendTextMessage(telefono, mensaje);
    }
}
