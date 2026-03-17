package cl.bunnycure.service;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.BookingRequestRepository;
import cl.bunnycure.exception.ResourceNotFoundException;
import cl.bunnycure.web.dto.AppointmentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final BookingRequestRepository bookingRequestRepository;
    private final CustomerService customerService;
    private final NotificationService notificationService;
    private final ServiceCatalogService serviceCatalogService;

    @Transactional
    public void updateAppointment(Long id, AppointmentDto dto) {
        var appointment = findById(id);
        var customer    = customerService.findById(dto.getCustomerId());
        var service     = serviceCatalogService.findById(dto.getServiceId());

        // Resetear recordatorio si cambia la fecha u hora para que vuelva a recibir aviso
        boolean dateOrTimeChanged = !dto.getAppointmentDate().equals(appointment.getAppointmentDate())
                || !dto.getAppointmentTime().equals(appointment.getAppointmentTime());
        if (dateOrTimeChanged) {
            appointment.setReminderSent(false);
            log.info("[APPOINTMENT] Cita {} reagendada a {}/{} — reminderSent reseteado",
                    id, dto.getAppointmentDate(), dto.getAppointmentTime());
        }

        appointment.setCustomer(customer);
        appointment.setService(service);
        appointment.setAppointmentDate(dto.getAppointmentDate());
        appointment.setAppointmentTime(dto.getAppointmentTime());
        appointment.setObservations(dto.getObservations());

        if (dto.getStatus() != appointment.getStatus()) {
            appointment.setStatus(dto.getStatus());
            if (dto.getStatus() == AppointmentStatus.CANCELLED) {
                notificationService.sendCancellationNotice(appointment);
            }
        }
        appointmentRepository.save(appointment);
    }

    @Transactional
    public void createAppointment(AppointmentDto dto) {
        var customer = customerService.findById(dto.getCustomerId());
        var service  = serviceCatalogService.findById(dto.getServiceId()); // ✅

        var appointment = Appointment.builder()
                .customer(customer)
                .service(service)
                .appointmentDate(dto.getAppointmentDate())
                .appointmentTime(dto.getAppointmentTime())
                .observations(dto.getObservations())
                .build();
        var saved = appointmentRepository.save(appointment);

        notificationService.sendConfirmation(saved);
        saved.setNotificationSent(true);
        appointmentRepository.save(saved);
    }

    @Transactional
    public Appointment updateStatus(Long id, AppointmentStatus newStatus) {
        var appointment = findById(id);
        appointment.setStatus(newStatus);

        if (newStatus == AppointmentStatus.CANCELLED) {
            notificationService.sendCancellationNotice(appointment);
        }
        return appointmentRepository.save(appointment);
    }

    public Appointment findById(Long id) {
        return appointmentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con ID: " + id));
    }

    // Alias para consistencia
    public Optional<Appointment> getAppointmentById(Long id) {
        return appointmentRepository.findByIdWithDetails(id);
    }

    public Appointment saveAppointment(Appointment appointment) {
        return appointmentRepository.save(appointment);
    }

    public List<Appointment> findByDateRange(LocalDate start, LocalDate end) {
        return appointmentRepository.findByDateRangeWithCustomer(start, end); // ← nombre actualizado
    }

    public List<Appointment> findTodayAppointments() {
        return appointmentRepository.findByDateWithCustomer(LocalDate.now());
    }

    @Transactional
    public void deleteAppointment(Long id) {
        var appointment = findById(id);
        // Defensive unlink: production DBs with legacy FK definitions may not have ON DELETE SET NULL.
        bookingRequestRepository.clearAppointmentByAppointmentId(id);
        appointmentRepository.delete(appointment);
    }

    @Transactional
    public void sendManualNotification(Long id) {
        var appointment = findById(id);
        notificationService.sendAppointmentConfirmation(appointment);
        appointment.setNotificationSent(true);
        appointmentRepository.save(appointment);
    }

    /**
     * Envía recordatorios para citas programadas para mañana.
     * Usa query JPQL filtrada por fecha para evitar traer toda la tabla a memoria.
     */
    @Transactional
    public void sendRemindersForUpcomingAppointments() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Appointment> appointments = appointmentRepository.findPendingRemindersForDateByStatuses(
                List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED),
                tomorrow
        );

        log.info("[REMINDER] {} citas encontradas para mañana ({})", appointments.size(), tomorrow);

        appointments.forEach(appointment -> {
            try {
                notificationService.sendReminderNotification(appointment, "tomorrow");
                appointment.setReminderSent(true);
                appointmentRepository.save(appointment);
            } catch (Exception e) {
                log.error("[REMINDER] Error enviando recordatorio día anterior para cita {}: {}",
                        appointment.getId(), e.getMessage(), e);
            }
        });
    }

    /**
     * Envía recordatorios para citas en las próximas 2 horas.
     * Usa query JPQL filtrada por fecha y ventana horaria para evitar traer toda la tabla a memoria.
     */
    @Transactional
    public void sendRemindersForAppointmentsIn2Hours() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        LocalTime inTwoHours = now.plusHours(2);

        List<Appointment> appointments = appointmentRepository.findPendingRemindersForDateAndTimeWindowByStatuses(
                List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED),
                today,
                now,
                inTwoHours
        );

        log.info("[REMINDER] {} citas encontradas en ventana 2h ({} - {})", appointments.size(), now, inTwoHours);

        appointments.forEach(appointment -> {
            try {
                notificationService.sendReminderNotification(appointment, "2hours");
                appointment.setReminderSent(true);
                appointmentRepository.save(appointment);
            } catch (Exception e) {
                log.error("[REMINDER] Error enviando recordatorio 2h para cita {}: {}",
                        appointment.getId(), e.getMessage(), e);
            }
        });
    }

    /**
     * Encuentra todas las citas con un estado específico
     */
    public List<Appointment> findByStatus(AppointmentStatus status) {
        return appointmentRepository.findByStatus(status);
    }

    /**
     * Guarda una cita
     */
    @Transactional
    public Appointment save(Appointment appointment) {
        return appointmentRepository.save(appointment);
    }
}