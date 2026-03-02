package cl.bunnycure.service;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.exception.ResourceNotFoundException;
import cl.bunnycure.web.dto.AppointmentDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final CustomerService customerService;
    private final NotificationService notificationService;
    private final ServiceCatalogService serviceCatalogService;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              CustomerService customerService,
                              NotificationService notificationService, ServiceCatalogService serviceCatalogService) {
        this.appointmentRepository = appointmentRepository;
        this.customerService = customerService;
        this.notificationService = notificationService;
        this.serviceCatalogService = serviceCatalogService;
    }

    @Transactional
    public void updateAppointment(Long id, AppointmentDto dto) {
        var appointment = findById(id);
        var customer    = customerService.findById(dto.getCustomerId());
        var service     = serviceCatalogService.findById(dto.getServiceId()); // ✅

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

        var appointment = new Appointment(customer, service,
                dto.getAppointmentDate(),
                dto.getAppointmentTime());
        appointment.setObservations(dto.getObservations());
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
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con ID: " + id));
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
        appointmentRepository.delete(appointment);
    }

    @Transactional
    public void sendManualNotification(Long id) {
        var appointment = findById(id);
        notificationService.sendConfirmation(appointment);
        appointment.setNotificationSent(true);
        appointmentRepository.save(appointment);
    }
}
