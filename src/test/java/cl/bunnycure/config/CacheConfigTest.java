package cl.bunnycure.config;

import cl.bunnycure.domain.model.AppSettings;
import cl.bunnycure.domain.repository.AppSettingsRepository;
import cl.bunnycure.service.AppSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {CacheConfig.class, AppSettingsService.class})
@ActiveProfiles("test")
class CacheConfigTest {

    @MockBean
    private AppSettingsRepository repository;

    @Autowired
    private AppSettingsService appSettingsService;

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
}
