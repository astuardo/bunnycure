package cl.bunnycure.config;

import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.User;
import cl.bunnycure.domain.repository.AppSettingsRepository;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.domain.repository.ServiceCatalogRepository;
import cl.bunnycure.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ServiceCatalogRepository serviceCatalogRepository;

    @Mock
    private AppSettingsRepository appSettingsRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Captor
    private ArgumentCaptor<List<Customer>> customersCaptor;

    private DataInitializer dataInitializer;

    @BeforeEach
    void setUp() {
        dataInitializer = new DataInitializer(
                customerRepository,
                serviceCatalogRepository,
                appSettingsRepository,
                userRepository,
                passwordEncoder
        );

        ReflectionTestUtils.setField(dataInitializer, "adminUsername", "admin");
        ReflectionTestUtils.setField(dataInitializer, "adminPassword", "secret");
        ReflectionTestUtils.setField(dataInitializer, "adminFullName", "Administrador Local");
        ReflectionTestUtils.setField(dataInitializer, "adminEmail", "admin@local.test");
    }

    @Test
    void run_shouldSeedAdminWithConfiguredIdentity() throws Exception {
        ReflectionTestUtils.setField(dataInitializer, "demoCustomersEnabled", false);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
        when(serviceCatalogRepository.count()).thenReturn(1L);
        when(appSettingsRepository.count()).thenReturn(1L);

        dataInitializer.run();

        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertEquals("admin", saved.getUsername());
        assertEquals("Administrador Local", saved.getFullName());
        assertEquals("admin@local.test", saved.getEmail());
        assertEquals("encoded-secret", saved.getPassword());

        verify(customerRepository, never()).saveAll(org.mockito.ArgumentMatchers.<List<Customer>>any());
    }

    @Test
    void run_shouldSeedNeutralDemoCustomersWhenEnabled() throws Exception {
        ReflectionTestUtils.setField(dataInitializer, "demoCustomersEnabled", true);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(User.builder().username("admin").password("encoded").enabled(true).role("ADMIN").build()));
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);
        when(serviceCatalogRepository.count()).thenReturn(1L);
        when(customerRepository.count()).thenReturn(0L);
        when(appSettingsRepository.count()).thenReturn(1L);

        dataInitializer.run();

        verify(customerRepository).saveAll(customersCaptor.capture());
        List<Customer> seededCustomers = customersCaptor.getValue();

        assertEquals(3, seededCustomers.size());
        assertTrue(seededCustomers.stream().allMatch(c -> c.getEmail() != null && c.getEmail().endsWith("@local.test")));
        assertTrue(seededCustomers.stream().noneMatch(c -> c.getEmail() != null && c.getEmail().contains("astuardobonilla")));
    }
}
