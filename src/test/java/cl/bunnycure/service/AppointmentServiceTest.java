package cl.bunnycure.service;

import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.BookingRequestRepository;
import cl.bunnycure.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private BookingRequestRepository bookingRequestRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ServiceCatalogService serviceCatalogService;

    private AppointmentService appointmentService;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(
                appointmentRepository,
                bookingRequestRepository,
                customerService,
                notificationService,
                serviceCatalogService
        );
    }

    @Test
    void deleteAppointment_shouldClearBookingLinksBeforeDelete() {
        Appointment appointment = Appointment.builder().id(35L).build();
        when(appointmentRepository.findByIdWithDetails(35L)).thenReturn(Optional.of(appointment));

        appointmentService.deleteAppointment(35L);

        verify(bookingRequestRepository).clearAppointmentByAppointmentId(35L);
        verify(appointmentRepository).delete(appointment);
    }

    @Test
    void deleteAppointment_shouldThrowWhenAppointmentDoesNotExist() {
        when(appointmentRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> appointmentService.deleteAppointment(99L));
    }
}
