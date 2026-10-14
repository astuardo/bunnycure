package cl.bunnycure.config;

import cl.bunnycure.domain.model.AppSettings;
import cl.bunnycure.domain.repository.AppSettingsRepository;
import cl.bunnycure.service.AppSettingsService;
import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.BookingRequestRepository;
import cl.bunnycure.domain.repository.ServiceCatalogRepository;
import cl.bunnycure.service.ServiceCatalogService;
import cl.bunnycure.web.dto.ServiceCatalogDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {CacheConfig.class, AppSettingsService.class, ServiceCatalogService.class})
@ActiveProfiles("test")
class CacheConfigTest {

    @MockitoBean
    private AppSettingsRepository repository;

    @MockitoBean
    private ServiceCatalogRepository serviceCatalogRepository;

    @MockitoBean
    private AppointmentRepository appointmentRepository;

    @MockitoBean
    private BookingRequestRepository bookingRequestRepository;

    @Autowired
    private AppSettingsService appSettingsService;

    @Autowired
    private ServiceCatalogService serviceCatalogService;

    @Test
    void get_CachesValueOnSubsequentCalls() {
        // Arrange
        String key = "test.cached.key";
        when(repository.findById(key)).thenReturn(Optional.of(new AppSettings(key, "cached-val", null)));

        // Act - Call 1 (hits repository)
        String val1 = appSettingsService.get(key, "default");
        // Act - Call 2 (should hit cache)
        String val2 = appSettingsService.get(key, "default");

        // Assert
        assertEquals("cached-val", val1);
        assertEquals("cached-val", val2);
        verify(repository, times(1)).findById(key);

        // Act - Set (evicts cache)
        when(repository.findById(key)).thenReturn(Optional.of(new AppSettings(key, "new-val", null)));
        appSettingsService.set(key, "new-val");

        // Act - Call 3 after eviction (hits repository again)
        String val3 = appSettingsService.get(key, "default");
        assertEquals("new-val", val3);
        verify(repository, times(3)).findById(key); // 1st read + 1 in set + 2nd read
    }

    @Test
    void serviceCatalog_CachesResultAndEvictsOnSave() {
        // Arrange
        ServiceCatalog item = ServiceCatalog.builder()
                .id(1L)
                .name("Manicure Rusa")
                .price(BigDecimal.valueOf(25000))
                .active(true)
                .build();
        when(serviceCatalogRepository.findByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(item));

        // Act - Call 1 (hits repository)
        List<ServiceCatalog> res1 = serviceCatalogService.findAllActive();
        // Act - Call 2 (hits cache)
        List<ServiceCatalog> res2 = serviceCatalogService.findAllActive();

        // Assert
        assertEquals(1, res1.size());
        assertEquals(1, res2.size());
        verify(serviceCatalogRepository, times(1)).findByActiveTrueOrderByDisplayOrderAsc();

        // Act - Save evicts cache
        ServiceCatalogDto dto = new ServiceCatalogDto();
        dto.setId(1L);
        dto.setName("Manicure Pro");
        dto.setPrice(BigDecimal.valueOf(28000));
        when(serviceCatalogRepository.findById(1L)).thenReturn(Optional.of(item));
        when(serviceCatalogRepository.save(any(ServiceCatalog.class))).thenReturn(item);

        serviceCatalogService.save(dto);

        // Act - Call 3 after eviction (hits repository again)
        serviceCatalogService.findAllActive();
        verify(serviceCatalogRepository, times(2)).findByActiveTrueOrderByDisplayOrderAsc();
    }
}
