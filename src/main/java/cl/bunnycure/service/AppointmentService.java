package cl.bunnycure.service;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.BookingRequestRepository;
import cl.bunnycure.exception.ResourceNotFoundException;
import cl.bunnycure.web.dto.AppointmentDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Validated
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final BookingRequestRepository bookingRequestRepository;
    private final CustomerService customerService;
    private final NotificationService notificationService;
    private final ServiceCatalogService serviceCatalogService;
    private final AppSettingsService appSettingsService;
    private final LoyaltyRewardService loyaltyRewardService;
    private final cl.bunnycure.domain.repository.LoyaltyRewardHistoryRepository loyaltyRewardHistoryRepository;

    @Transactional
    public Appointment updateAppointment(@NotNull Long id, @Valid @NotNull AppointmentDto dto) {
        var appointment = findById(id);
        var customer    = customerService.findById(dto.getCustomerId());
        var selectedServices = resolveSelectedServices(dto);
        var primaryService = selectedServices.get(0);

        boolean dateChanged = !dto.getAppointmentDate().equals(appointment.getAppointmentDate());
        boolean timeChanged = !dto.getAppointmentTime().equals(appointment.getAppointmentTime());

        // Resetear recordatorio si cambia la fecha u hora para que vuelva a recibir aviso
        boolean dateOrTimeChanged = dateChanged || timeChanged;
        if (dateOrTimeChanged) {
            appointment.setReminderSent(false);
            log.info("[APPOINTMENT] Cita {} reagendada a {}/{} — reminderSent reseteado",
                    id, dto.getAppointmentDate(), dto.getAppointmentTime());
        }

        appointment.setCustomer(customer);
        appointment.setService(primaryService);
        appointment.setServices(new ArrayList<>(selectedServices));
        appointment.setAppointmentDate(dto.getAppointmentDate());
        appointment.setAppointmentTime(dto.getAppointmentTime());
        appointment.setObservations(dto.getObservations());

        if (dto.getStatus() != appointment.getStatus()) {
            appointment.setStatus(dto.getStatus());
            if (dto.getStatus() == AppointmentStatus.CANCELLED) {
                notificationService.sendCancellationNotice(appointment);
            }
        }

        if (dateOrTimeChanged && appointment.getStatus() != AppointmentStatus.CANCELLED) {
            notificationService.sendAppointmentRescheduleNotice(appointment, dateChanged, timeChanged);
        }

        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment createAppointment(@Valid @NotNull AppointmentDto dto) {
        var customer = customerService.findById(dto.getCustomerId());
        var selectedServices = resolveSelectedServices(dto);
        var primaryService = selectedServices.get(0);

        var appointment = Appointment.builder()
                .customer(customer)
                .service(primaryService)
                .services(new ArrayList<>(selectedServices))
                .appointmentDate(dto.getAppointmentDate())
                .appointmentTime(dto.getAppointmentTime())
                .observations(dto.getObservations())
                .build();
        var saved = appointmentRepository.save(appointment);

        // Enviar notificaciones respetando preferencias del cliente + notificar admin
        notificationService.sendAppointmentConfirmation(saved);
        saved.setNotificationSent(true);
        return appointmentRepository.save(saved);
    }

    @Transactional
    public Appointment updateStatus(Long id, AppointmentStatus newStatus) {
        var appointment = findById(id);
        AppointmentStatus oldStatus = appointment.getStatus();
        
        if (oldStatus != newStatus) {
            appointment.setStatus(newStatus);
    
            if (newStatus == AppointmentStatus.CANCELLED) {
                notificationService.sendCancellationNotice(appointment);
            }
            
            handleLoyaltyStamps(appointment, oldStatus, newStatus);
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
        LocalDate tomorrow = getNowInConfiguredZone().toLocalDate().plusDays(1);
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
        ZonedDateTime nowInZone = getNowInConfiguredZone();
        LocalDate today = nowInZone.toLocalDate();
        LocalTime now = nowInZone.toLocalTime();
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

    /**
     * Encuentra citas próximas en una ventana de tiempo.
     * Retorna solo citas confirmadas que NO tienen reminderSent = true.
     * Útil para notificaciones push automáticas desde el frontend.
     * 
     * @param hours Ventana de tiempo en horas desde ahora
     * @return Lista de citas en la ventana especificada
     */
    public List<Appointment> findUpcomingAppointmentsInWindow(int hours) {
        ZonedDateTime nowInZone = getNowInConfiguredZone();
        LocalDate today = nowInZone.toLocalDate();
        LocalTime now = nowInZone.toLocalTime();
        LocalTime endTime = now.plusHours(hours);

        log.debug("[APPOINTMENT] Buscando citas entre {} y {} (ventana de {}h)", now, endTime, hours);

        // Buscar citas de hoy en la ventana de tiempo especificada
        List<Appointment> appointments = appointmentRepository.findPendingRemindersForDateAndTimeWindowByStatuses(
                List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.PENDING),
                today,
                now,
                endTime
        );

        log.info("[APPOINTMENT] Encontradas {} citas en ventana de {}h", appointments.size(), hours);
        return appointments;
    }

    private ZonedDateTime getNowInConfiguredZone() {
        return ZonedDateTime.now(resolveReminderZoneId());
    }

    private ZoneId resolveReminderZoneId() {
        String timezone = appSettingsService.getAppTimezone();
        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            log.warn("[REMINDER] Timezone invalida '{}' en app settings. Usando fallback America/Santiago", timezone);
            return ZoneId.of("America/Santiago");
        }
    }

    private List<cl.bunnycure.domain.model.ServiceCatalog> resolveSelectedServices(AppointmentDto dto) {
        List<Long> serviceIds = dto.getServiceIds() != null && !dto.getServiceIds().isEmpty()
                ? dto.getServiceIds()
                : (dto.getServiceId() != null ? List.of(dto.getServiceId()) : List.of());

        List<cl.bunnycure.domain.model.ServiceCatalog> services;
        if (serviceIds.size() == 1) {
            services = List.of(serviceCatalogService.findById(serviceIds.get(0)));
        } else {
            services = serviceCatalogService.findByIds(serviceIds);
        }
        if (services.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos un servicio");
        }
        return services;
    }

    private void handleLoyaltyStamps(Appointment appointment, AppointmentStatus oldStatus, AppointmentStatus newStatus) {
        if (oldStatus != AppointmentStatus.COMPLETED && newStatus == AppointmentStatus.COMPLETED) {
            var customer = appointment.getCustomer();
            if (customer != null) {
                int currentStamps = customer.getLoyaltyStamps() != null ? customer.getLoyaltyStamps() : 0;
                int totalVisits = customer.getTotalCompletedVisits() != null ? customer.getTotalCompletedVisits() : 0;
                int rewardIndex = customer.getCurrentRewardIndex() != null ? customer.getCurrentRewardIndex() : 0;

                if (currentStamps < 10) {
                    // Flujo normal de acumulación
                    customer.setLoyaltyStamps(currentStamps + 1);
                    customer.setTotalCompletedVisits(totalVisits + 1);
                    log.info("[LOYALTY] Cliente {} suma sello {}/10", customer.getId(), customer.getLoyaltyStamps());
                } else {
                    // ESTA ES LA CITA #11 (LA DEL PREMIO)
                    // 1. Obtener el premio actual
                    var reward = loyaltyRewardService.getRewardAt(rewardIndex);
                    String rewardName = (reward != null) ? reward.getName() : "Premio BunnyCure";

                    // 2. Registrar en el historial
                    var history = cl.bunnycure.domain.model.LoyaltyRewardHistory.builder()
                            .customer(customer)
                            .rewardName(rewardName)
                            .earnedAt(java.time.LocalDateTime.now())
                            .appointment(appointment)
                            .build();
                    loyaltyRewardHistoryRepository.save(history);

                    // 3. Reiniciar contador y avanzar ciclo
                    customer.setLoyaltyStamps(0);
                    customer.setCurrentRewardIndex(rewardIndex + 1);
                    customer.setTotalCompletedVisits(totalVisits + 1);
                    
                    log.info("[LOYALTY] Cliente {} canjeó premio '{}'. Reiniciando ciclo.", customer.getId(), rewardName);
                }

                // Enviar la notificación de actualización (que ahora incluirá la info de sellos)
                notificationService.sendLoyaltyUpdateNotification(customer);
            }
        } else if (oldStatus == AppointmentStatus.COMPLETED && newStatus != AppointmentStatus.COMPLETED) {
            // Rollback si se cambia de COMPLETADA a otro estado (ej. error humano)
            var customer = appointment.getCustomer();
            if (customer != null) {
                int currentStamps = customer.getLoyaltyStamps() != null ? customer.getLoyaltyStamps() : 0;
                int totalVisits = customer.getTotalCompletedVisits() != null ? customer.getTotalCompletedVisits() : 0;

                if (currentStamps > 0) {
                    customer.setLoyaltyStamps(currentStamps - 1);
                } else {
                    // Si estaba en 0 es porque justo había completado un ciclo, 
                    // para un rollback perfecto habría que revertir el rewardIndex también.
                    // Por simplicidad en este MVP, solo evitamos negativos.
                }
                if (totalVisits > 0) customer.setTotalCompletedVisits(totalVisits - 1);
            }
        }
    }
}
