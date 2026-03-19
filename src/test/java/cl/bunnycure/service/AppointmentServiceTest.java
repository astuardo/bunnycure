package cl.bunnycure.service;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.BookingRequestRepository;
import cl.bunnycure.exception.ResourceNotFoundException;
import cl.bunnycure.web.dto.AppointmentDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    @Mock
    private AppSettingsService appSettingsService;

    private AppointmentService appointmentService;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(
                appointmentRepository,
                bookingRequestRepository,
                customerService,
                notificationService,
                serviceCatalogService,
                appSettingsService
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

    @Test
    void sendManualNotification_shouldUseAppointmentConfirmationFlow() {
        Appointment appointment = Appointment.builder().id(40L).notificationSent(false).build();
        when(appointmentRepository.findByIdWithDetails(40L)).thenReturn(Optional.of(appointment));

        appointmentService.sendManualNotification(40L);

        verify(notificationService).sendAppointmentConfirmation(appointment);
        verify(appointmentRepository).save(appointment);
    }

    // ── Reschedule reminder reset ────────────────────────────────────────────

    @Test
    void updateAppointment_shouldResetReminderSentWhenDateChanges() {
        LocalDate originalDate = LocalDate.of(2026, 3, 15);
        LocalDate newDate      = LocalDate.of(2026, 3, 20);
        LocalTime time         = LocalTime.of(11, 0);

        Appointment appointment = Appointment.builder()
                .id(10L)
                .customer(new Customer("Ana", "+56912345678", null))
                .service(ServiceCatalog.builder().id(1L).name("Manicure").build())
                .appointmentDate(originalDate)
                .appointmentTime(time)
                .status(AppointmentStatus.CONFIRMED)
                .reminderSent(true)
                .build();

        AppointmentDto dto = AppointmentDto.builder()
                .customerId(1L)
                .serviceId(1L)
                .appointmentDate(newDate)
                .appointmentTime(time)
                .status(AppointmentStatus.CONFIRMED)
                .build();

        when(appointmentRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(appointment));
        when(customerService.findById(1L)).thenReturn(appointment.getCustomer());
        when(serviceCatalogService.findById(1L)).thenReturn(appointment.getService());
        when(appointmentRepository.save(any())).thenReturn(appointment);

        appointmentService.updateAppointment(10L, dto);

        assertFalse(appointment.isReminderSent(),
                "reminderSent debe resetearse a false cuando cambia la fecha de la cita");
    }

    @Test
    void updateAppointment_shouldResetReminderSentWhenTimeChanges() {
        LocalDate date          = LocalDate.of(2026, 3, 15);
        LocalTime originalTime  = LocalTime.of(11, 0);
        LocalTime newTime       = LocalTime.of(15, 0);

        Appointment appointment = Appointment.builder()
                .id(11L)
                .customer(new Customer("Vale", "+56987654321", null))
                .service(ServiceCatalog.builder().id(2L).name("Pedicure").build())
                .appointmentDate(date)
                .appointmentTime(originalTime)
                .status(AppointmentStatus.CONFIRMED)
                .reminderSent(true)
                .build();

        AppointmentDto dto = AppointmentDto.builder()
                .customerId(1L)
                .serviceId(2L)
                .appointmentDate(date)
                .appointmentTime(newTime)
                .status(AppointmentStatus.CONFIRMED)
                .build();

        when(appointmentRepository.findByIdWithDetails(11L)).thenReturn(Optional.of(appointment));
        when(customerService.findById(1L)).thenReturn(appointment.getCustomer());
        when(serviceCatalogService.findById(2L)).thenReturn(appointment.getService());
        when(appointmentRepository.save(any())).thenReturn(appointment);

        appointmentService.updateAppointment(11L, dto);

        assertFalse(appointment.isReminderSent(),
                "reminderSent debe resetearse a false cuando cambia la hora de la cita");
    }

    @Test
    void updateAppointment_shouldKeepReminderSentWhenDateAndTimeUnchanged() {
        LocalDate date = LocalDate.of(2026, 3, 15);
        LocalTime time = LocalTime.of(11, 0);

        Appointment appointment = Appointment.builder()
                .id(12L)
                .customer(new Customer("Javi", "+56911223344", null))
                .service(ServiceCatalog.builder().id(1L).name("Manicure").build())
                .appointmentDate(date)
                .appointmentTime(time)
                .status(AppointmentStatus.CONFIRMED)
                .reminderSent(true)
                .build();

        AppointmentDto dto = AppointmentDto.builder()
                .customerId(1L)
                .serviceId(1L)
                .appointmentDate(date)
                .appointmentTime(time)
                .status(AppointmentStatus.CONFIRMED)
                .observations("Solo se actualiza la observación")
                .build();

        when(appointmentRepository.findByIdWithDetails(12L)).thenReturn(Optional.of(appointment));
        when(customerService.findById(1L)).thenReturn(appointment.getCustomer());
        when(serviceCatalogService.findById(1L)).thenReturn(appointment.getService());
        when(appointmentRepository.save(any())).thenReturn(appointment);

        appointmentService.updateAppointment(12L, dto);

        assertTrue(appointment.isReminderSent(),
                "reminderSent no debe cambiar si la fecha y hora son las mismas");
    }

    @Test
    void sendRemindersForUpcomingAppointments_shouldFallbackWhenTimezoneIsInvalid() {
        when(appSettingsService.getAppTimezone()).thenReturn("invalid/timezone");
        when(appointmentRepository.findPendingRemindersForDateByStatuses(anyList(), any(LocalDate.class)))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> appointmentService.sendRemindersForUpcomingAppointments());

        verify(appointmentRepository).findPendingRemindersForDateByStatuses(anyList(), any(LocalDate.class));
    }

    @Test
    void sendRemindersForAppointmentsIn2Hours_shouldFallbackWhenTimezoneIsInvalid() {
        when(appSettingsService.getAppTimezone()).thenReturn("invalid/timezone");
        when(appointmentRepository.findPendingRemindersForDateAndTimeWindowByStatuses(
                anyList(), any(LocalDate.class), any(LocalTime.class), any(LocalTime.class)))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> appointmentService.sendRemindersForAppointmentsIn2Hours());

        verify(appointmentRepository).findPendingRemindersForDateAndTimeWindowByStatuses(
                anyList(), any(LocalDate.class), any(LocalTime.class), any(LocalTime.class));
    }
}
