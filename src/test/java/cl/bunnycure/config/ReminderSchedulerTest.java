package cl.bunnycure.config;

import cl.bunnycure.service.AppointmentService;
import cl.bunnycure.service.AppSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderSchedulerTest {

    @Mock
    private AppointmentService appointmentService;

    @Mock
    private AppSettingsService appSettingsService;

    @Mock
    private cl.bunnycure.service.TenantExecutionService tenantExecutionService;

    @InjectMocks
    private ReminderScheduler reminderScheduler;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().doAnswer(inv -> {
            java.util.function.Consumer<cl.bunnycure.domain.model.Tenant> action = inv.getArgument(1);
            cl.bunnycure.domain.model.Tenant tenant = cl.bunnycure.domain.model.Tenant.builder().id(1L).name("BunnyCure").build();
            action.accept(tenant);
            return null;
        }).when(tenantExecutionService).executeForAllActiveTenants(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendDayBeforeReminders_shouldSkipWhenStrategyDisabled() {
        when(appSettingsService.isReminderDayBeforeEnabled()).thenReturn(false);
        when(appSettingsService.getReminderStrategy()).thenReturn("2hours");

        reminderScheduler.sendDayBeforeReminders();

        verify(appointmentService, never()).sendRemindersForUpcomingAppointments();
    }

    @Test
    void sendDayBeforeReminders_shouldExecuteWhenStrategyEnabled() {
        when(appSettingsService.isReminderDayBeforeEnabled()).thenReturn(true);

        reminderScheduler.sendDayBeforeReminders();

        verify(appointmentService).sendRemindersForUpcomingAppointments();
    }

    @Test
    void sendTwoHourReminders_shouldSkipWhenStrategyDisabled() {
        when(appSettingsService.isReminder2HoursEnabled()).thenReturn(false);
        when(appSettingsService.getReminderStrategy()).thenReturn("day_before");

        reminderScheduler.sendTwoHourReminders();

        verify(appointmentService, never()).sendRemindersForAppointmentsIn2Hours();
    }

    @Test
    void sendTwoHourReminders_shouldExecuteWhenStrategyEnabled() {
        when(appSettingsService.isReminder2HoursEnabled()).thenReturn(true);

        reminderScheduler.sendTwoHourReminders();

        verify(appointmentService).sendRemindersForAppointmentsIn2Hours();
    }
}
