package cl.bunnycure.service;

import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.BookingRequestRepository;
import cl.bunnycure.domain.repository.ServiceCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceCatalogServiceTest {

    @Mock
    private ServiceCatalogRepository serviceCatalogRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private BookingRequestRepository bookingRequestRepository;

    private ServiceCatalogService serviceCatalogService;

    @BeforeEach
    void setUp() {
        serviceCatalogService = new ServiceCatalogService(
                serviceCatalogRepository,
                appointmentRepository,
                bookingRequestRepository
        );
    }

    @Test
    void delete_WhenServiceHasReferences_DeactivatesInsteadOfDeleting() {
        ServiceCatalog service = ServiceCatalog.builder()
                .id(5L)
                .name("Manicure")
                .active(true)
                .build();

        when(serviceCatalogRepository.findById(5L)).thenReturn(Optional.of(service));
        when(appointmentRepository.countByServiceId(5L)).thenReturn(1L);
        when(bookingRequestRepository.countByServiceId(5L)).thenReturn(0L);

        ServiceCatalogService.DeleteOutcome outcome = serviceCatalogService.delete(5L);

        assertEquals(ServiceCatalogService.DeleteOutcome.DEACTIVATED_REFERENCED, outcome);
        verify(serviceCatalogRepository).save(service);
        verify(serviceCatalogRepository, never()).deleteById(5L);
    }

    @Test
    void delete_WhenServiceHasNoReferences_DeletesService() {
        ServiceCatalog service = ServiceCatalog.builder()
                .id(6L)
                .name("Pedicure")
                .active(true)
                .build();

        when(serviceCatalogRepository.findById(6L)).thenReturn(Optional.of(service));
        when(appointmentRepository.countByServiceId(6L)).thenReturn(0L);
        when(bookingRequestRepository.countByServiceId(6L)).thenReturn(0L);

        ServiceCatalogService.DeleteOutcome outcome = serviceCatalogService.delete(6L);

        assertEquals(ServiceCatalogService.DeleteOutcome.DELETED, outcome);
        verify(serviceCatalogRepository).deleteById(6L);
        verify(serviceCatalogRepository, never()).save(service);
    }
}
