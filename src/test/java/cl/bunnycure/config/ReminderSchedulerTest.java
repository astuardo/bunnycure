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

    @InjectMocks
    private ReminderScheduler reminderScheduler;

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
