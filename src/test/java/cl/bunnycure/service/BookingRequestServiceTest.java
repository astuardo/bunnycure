package cl.bunnycure.service;

import cl.bunnycure.domain.enums.BookingRequestStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.BookingRequest;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.BookingRequestRepository;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.domain.repository.ServiceCatalogRepository;
import cl.bunnycure.web.dto.BookingApprovalDto;
import cl.bunnycure.web.dto.BookingRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingRequestServiceTest {

    @Mock
    private BookingRequestRepository bookingRequestRepository;

    @Mock
    private ServiceCatalogRepository serviceCatalogRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private NotificationService notificationService;

    private BookingRequestService bookingRequestService;

    @BeforeEach
    void setUp() {
        bookingRequestService = new BookingRequestService(
                bookingRequestRepository,
                serviceCatalogRepository,
                customerRepository,
                appointmentRepository,
                notificationService
        );
    }

    @Test
    void approve_shouldUseAdminSelectedDateAndTime_insteadOfPreferredDate() {
        Long requestId = 169L;
        LocalDate preferredDate = LocalDate.of(2026, 3, 20);
        LocalDate approvedDate = LocalDate.of(2026, 3, 27);
        LocalTime approvedTime = LocalTime.of(16, 30);

        ServiceCatalog requestedService = ServiceCatalog.builder()
                .id(7L)
                .name("Limpieza facial")
                .build();

        BookingRequest request = BookingRequest.builder()
                .id(requestId)
                .fullName("Camila")
                .phone("+56911112222")
                .email("camila@example.com")
                .service(requestedService)
                .preferredDate(preferredDate)
                .status(BookingRequestStatus.PENDING)
                .build();

        Customer customer = new Customer("Camila", "+56911112222", "camila@example.com");

        BookingApprovalDto approval = new BookingApprovalDto();
        approval.setAppointmentDate(approvedDate);
        approval.setAppointmentTime(approvedTime);

        when(bookingRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(customerRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(customer));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            appointment.setId(501L);
            return appointment;
        });

        Appointment result = bookingRequestService.approve(requestId, approval);

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(appointmentCaptor.capture());
        Appointment savedAppointment = appointmentCaptor.getValue();

        assertEquals(approvedDate, savedAppointment.getAppointmentDate());
        assertEquals(approvedTime, savedAppointment.getAppointmentTime());
        assertEquals(preferredDate, request.getPreferredDate());
        assertEquals(BookingRequestStatus.APPROVED, request.getStatus());
        assertSame(result, request.getAppointment());
        verify(notificationService).sendAppointmentConfirmation(result);
    }

    @Test
    void approve_shouldRejectPastDate() {
        Long requestId = 170L;
        LocalDate today = LocalDate.now();

        ServiceCatalog requestedService = ServiceCatalog.builder().id(7L).name("Limpieza facial").build();
        BookingRequest request = BookingRequest.builder()
                .id(requestId)
                .fullName("Sofia")
                .phone("+56933334444")
                .service(requestedService)
                .preferredDate(today)
                .status(BookingRequestStatus.PENDING)
                .build();

        BookingApprovalDto approval = new BookingApprovalDto();
        approval.setAppointmentDate(today.minusDays(1));
        approval.setAppointmentTime(LocalTime.of(10, 0));

        when(bookingRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> bookingRequestService.approve(requestId, approval));

        assertEquals("La fecha de la cita no puede ser anterior a hoy.", ex.getMessage());
    }

    @Test
    void approve_shouldAllowTodayDate() {
        Long requestId = 171L;
        LocalDate today = LocalDate.now();
        LocalTime approvedTime = LocalTime.of(12, 0);

        ServiceCatalog requestedService = ServiceCatalog.builder().id(7L).name("Limpieza facial").build();
        BookingRequest request = BookingRequest.builder()
                .id(requestId)
                .fullName("Valentina")
                .phone("+56955556666")
                .service(requestedService)
                .preferredDate(today.plusDays(1))
                .status(BookingRequestStatus.PENDING)
                .build();

        Customer customer = new Customer("Valentina", "+56955556666", null);

        BookingApprovalDto approval = new BookingApprovalDto();
        approval.setAppointmentDate(today);
        approval.setAppointmentTime(approvedTime);

        when(bookingRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(customerRepository.findByPhone(request.getPhone())).thenReturn(Optional.of(customer));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            appointment.setId(777L);
            return appointment;
        });

        Appointment result = bookingRequestService.approve(requestId, approval);

        assertEquals(today, result.getAppointmentDate());
        assertEquals(approvedTime, result.getAppointmentTime());
    }

    @Test
    void create_shouldQueueAdminAlertAndNotifyCustomerWhenEmailPresent() {
        Long serviceId = 9L;

        ServiceCatalog service = ServiceCatalog.builder()
                .id(serviceId)
                .name("Limpieza facial")
                .build();

        BookingRequestDto dto = new BookingRequestDto();
        dto.setFullName("Camila Perez");
        dto.setPhone("+56911112222");
        dto.setGender("FEMENINO");
        dto.setBirthDate(LocalDate.of(1998, 4, 10));
        dto.setEmail("camila@example.com");
        dto.setServiceId(serviceId);
        dto.setPreferredDate(LocalDate.of(2026, 4, 2));
        dto.setPreferredBlock("Tarde");
        dto.setNotes("Prefiere WhatsApp");

        when(serviceCatalogRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(customerRepository.findByPhone(dto.getPhone())).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRequestRepository.save(any(BookingRequest.class))).thenAnswer(invocation -> {
            BookingRequest request = invocation.getArgument(0);
            request.setId(1001L);
            return request;
        });

        BookingRequest created = bookingRequestService.create(dto);

        verify(notificationService).sendBookingRequestReceived(eq(created));
        verify(notificationService).queueAdminNewBookingAlert(eq(created));
    }

    @Test
    void create_shouldQueueAdminAlertEvenWithoutEmail() {
        Long serviceId = 10L;

        ServiceCatalog service = ServiceCatalog.builder()
                .id(serviceId)
                .name("Drenaje linfatico")
                .build();

        BookingRequestDto dto = new BookingRequestDto();
        dto.setFullName("Sofia Rojas");
        dto.setPhone("+56933334444");
        dto.setGender("FEMENINO");
        dto.setBirthDate(LocalDate.of(1995, 8, 20));
        dto.setEmail(" ");
        dto.setServiceId(serviceId);
        dto.setPreferredDate(LocalDate.of(2026, 4, 5));
        dto.setPreferredBlock("Manana");

        when(serviceCatalogRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(customerRepository.findByPhone(dto.getPhone())).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRequestRepository.save(any(BookingRequest.class))).thenAnswer(invocation -> {
            BookingRequest request = invocation.getArgument(0);
            request.setId(1002L);
            return request;
        });

        BookingRequest created = bookingRequestService.create(dto);

        verify(notificationService, never()).sendBookingRequestReceived(any(BookingRequest.class));
        verify(notificationService).queueAdminNewBookingAlert(eq(created));
    }
}
