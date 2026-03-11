package cl.bunnycure.service;

import cl.bunnycure.domain.repository.WebhookOperationalEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebhookOperationalEventMaintenanceServiceTest {

    @Mock
    private WebhookOperationalEventRepository webhookOperationalEventRepository;

    private WebhookOperationalEventMaintenanceService maintenanceService;

    @BeforeEach
    void setUp() {
        maintenanceService = new WebhookOperationalEventMaintenanceService(webhookOperationalEventRepository);
    }

    @Test
    void cleanupOldOperationalEvents_WhenEnabled_DeletesOldRecords() {
        ReflectionTestUtils.setField(maintenanceService, "cleanupEnabled", true);
        ReflectionTestUtils.setField(maintenanceService, "retentionDays", 30);

        maintenanceService.cleanupOldOperationalEvents();

        verify(webhookOperationalEventRepository).deleteByCreatedAtBefore(any());
    }

    @Test
    void cleanupOldOperationalEvents_WhenDisabled_DoesNothing() {
        ReflectionTestUtils.setField(maintenanceService, "cleanupEnabled", false);

        maintenanceService.cleanupOldOperationalEvents();

        verify(webhookOperationalEventRepository, never()).deleteByCreatedAtBefore(any());
    }

    @Test
    void cleanupOldOperationalEvents_WhenRetentionInvalid_DoesNothing() {
        ReflectionTestUtils.setField(maintenanceService, "cleanupEnabled", true);
        ReflectionTestUtils.setField(maintenanceService, "retentionDays", 0);

        maintenanceService.cleanupOldOperationalEvents();

        verify(webhookOperationalEventRepository, never()).deleteByCreatedAtBefore(any());
    }

    @Test
    void cleanupOldOperationalEvents_WhenDeleteFails_DoesNotThrow() {
        ReflectionTestUtils.setField(maintenanceService, "cleanupEnabled", true);
        ReflectionTestUtils.setField(maintenanceService, "retentionDays", 30);
        doThrow(new RuntimeException("db unavailable"))
                .when(webhookOperationalEventRepository)
                .deleteByCreatedAtBefore(any());

        maintenanceService.cleanupOldOperationalEvents();

        verify(webhookOperationalEventRepository).deleteByCreatedAtBefore(any());
    }
}
