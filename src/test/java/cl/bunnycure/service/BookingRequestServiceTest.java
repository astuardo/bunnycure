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
import static org.mockito.ArgumentMatchers.any;
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
}
