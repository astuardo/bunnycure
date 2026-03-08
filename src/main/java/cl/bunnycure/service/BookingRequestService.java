package cl.bunnycure.service;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.enums.BookingRequestStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.BookingRequest;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.BookingRequestRepository;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.domain.repository.ServiceCatalogRepository;
import cl.bunnycure.exception.ResourceNotFoundException;
import cl.bunnycure.web.dto.BookingApprovalDto;
import cl.bunnycure.web.dto.BookingRequestDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BookingRequestService {

    private final BookingRequestRepository bookingRequestRepository;
    private final ServiceCatalogRepository serviceCatalogRepository;
    private final CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;

    public BookingRequestService(BookingRequestRepository bookingRequestRepository,
                                 ServiceCatalogRepository serviceCatalogRepository,
                                 CustomerRepository customerRepository,
                                 AppointmentRepository appointmentRepository,
                                 NotificationService notificationService) {
        this.bookingRequestRepository = bookingRequestRepository;
        this.serviceCatalogRepository = serviceCatalogRepository;
        this.customerRepository = customerRepository;
        this.appointmentRepository = appointmentRepository;
        this.notificationService = notificationService;
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    public List<BookingRequest> findPending() {
        return bookingRequestRepository.findPendingWithService(BookingRequestStatus.PENDING);
    }

    public List<BookingRequest> findAll() {
        return bookingRequestRepository.findAllWithService();
    }

    public long countPending() {
        return bookingRequestRepository.countByStatus(BookingRequestStatus.PENDING);
    }

    public BookingRequest findById(Long id) {
        return bookingRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada: " + id));
    }

    // ── Crear solicitud (desde portal público) ───────────────────────────────

    @Transactional
    public BookingRequest create(BookingRequestDto dto) {
        var service = serviceCatalogRepository.findById(dto.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado"));

        var request = BookingRequest.builder()
                .fullName(dto.getFullName())
                .phone(dto.getPhone())
                .email(dto.getEmail())
                .service(service)
                .preferredDate(dto.getPreferredDate())
                .preferredBlock(dto.getPreferredBlock())
                .notes(dto.getNotes())
                .status(BookingRequestStatus.PENDING)
                .build();

        var saved = bookingRequestRepository.save(request);

        // Email de recepción a la solicitante (si tiene email)
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            notificationService.sendBookingRequestReceived(saved);
        }

        return saved;
    }

    // ── Aprobar solicitud ────────────────────────────────────────────────────

    @Transactional
    public Appointment approve(Long requestId, BookingApprovalDto approval) {
        var request = findById(requestId);

        if (request.getStatus() != BookingRequestStatus.PENDING) {
            throw new IllegalStateException("Esta solicitud ya fue procesada.");
        }

        // 1. Buscar o crear la clienta
        Customer customer = customerRepository.findByPhone(request.getPhone())
                .orElseGet(() -> {
                    // Si no existe, la creamos automáticamente
                    var newCustomer = new Customer(
                            request.getFullName(),
                            request.getPhone(),
                            request.getEmail() != null ? request.getEmail() : ""
                    );
                    return customerRepository.save(newCustomer);
                });

        // Actualizar email si no lo tenía
        if ((customer.getEmail() == null || customer.getEmail().isBlank())
                && request.getEmail() != null && !request.getEmail().isBlank()) {
            customer.setEmail(request.getEmail());
            customerRepository.save(customer);
        }

        // 2. Determinar el servicio final (puede cambiar al aprobar)
        var service = (approval.getServiceId() != null)
                ? serviceCatalogRepository.findById(approval.getServiceId())
                .orElse(request.getService())
                : request.getService();

        // 3. Crear la cita
        var appointment = Appointment.builder()
                .customer(customer)
                .service(service)
                .appointmentDate(request.getPreferredDate())
                .appointmentTime(approval.getAppointmentTime())
                .status(AppointmentStatus.PENDING)
                .observations(approval.getAdminNotes())
                .notificationSent(false)
                .build();

        var savedAppointment = appointmentRepository.save(appointment);

        // 4. Actualizar la solicitud
        request.setStatus(BookingRequestStatus.APPROVED);
        request.setAppointment(savedAppointment);
        request.setResolvedAt(LocalDateTime.now());
        bookingRequestRepository.save(request);

        // 5. Notificar a la clienta
        notificationService.sendAppointmentConfirmation(savedAppointment);

        return savedAppointment;
    }

    // ── Rechazar solicitud ───────────────────────────────────────────────────

    @Transactional
    public void reject(Long requestId, String reason) {
        var request = findById(requestId);

        if (request.getStatus() != BookingRequestStatus.PENDING) {
            throw new IllegalStateException("Esta solicitud ya fue procesada.");
        }

        request.setStatus(BookingRequestStatus.REJECTED);
        request.setRejectionReason(reason);
        request.setResolvedAt(LocalDateTime.now());
        bookingRequestRepository.save(request);

        // Notificar rechazo si tiene email
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            notificationService.sendBookingRequestRejected(request);
        }
    }
}