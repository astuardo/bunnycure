package cl.bunnycure.service;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.web.dto.CustomerSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private GoogleWalletService googleWalletService;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository, googleWalletService);
    }

    @Test
    void searchSummary_WhenSearchingWithoutAccents_FindsCustomerWithAccents() {
        // Arrange
        Customer sofia = new Customer();
        sofia.setId(1L);
        sofia.setFullName("Sofía Belén Fernandez");
        sofia.setPhone("+56946973351");
        sofia.setRut("18.664.589-8");

        Customer alfredo = new Customer();
        alfredo.setId(2L);
        alfredo.setFullName("Alfredo Stuardo");
        alfredo.setPhone("+56983692046");

        List<Object[]> rows = Arrays.asList(
                new Object[]{sofia, 2L},
                new Object[]{alfredo, 0L}
        );
        when(customerRepository.findAllWithAppointmentCount())
                .thenReturn(rows);

        // Act - searching "sofia" (no accent) matches "Sofía"
        List<CustomerSummary> result = customerService.searchSummary("sofia");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Sofía Belén Fernandez", result.get(0).getCustomer().getFullName());
    }

    @Test
    void searchSummary_WhenSearchingWithAccents_FindsCustomerWithoutAccents() {
        // Arrange
        Customer maria = new Customer();
        maria.setId(1L);
        maria.setFullName("Maria Gonzalez");
        maria.setPhone("+56911223344");

        List<Object[]> rows = Collections.singletonList(new Object[]{maria, 1L});
        when(customerRepository.findAllWithAppointmentCount()).thenReturn(rows);

        // Act - searching "María" matches "Maria"
        List<CustomerSummary> result = customerService.searchSummary("María");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Maria Gonzalez", result.get(0).getCustomer().getFullName());
    }

    @Test
    void searchSummary_WhenSearchingByRutWithoutDots_MatchesFormattedRut() {
        // Arrange
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Camila Reyes");
        customer.setPhone("+56964499995");
        customer.setRut("18.664.589-8");

        List<Object[]> rows = Collections.singletonList(new Object[]{customer, 0L});
        when(customerRepository.findAllWithAppointmentCount()).thenReturn(rows);

        // Act - searching "186645898" matches "18.664.589-8"
        List<CustomerSummary> result = customerService.searchSummary("186645898");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Camila Reyes", result.get(0).getCustomer().getFullName());
    }

    @Test
    void searchSummary_WhenBlankQuery_CallsFindAllSummary() {
        // Arrange
        Customer customer = new Customer();
        customer.setId(2L);
        customer.setFullName("Daniela Rojas");

        Object[] row = new Object[]{customer, 3L};
        List<Object[]> rows = Collections.singletonList(row);
        when(customerRepository.findAllWithAppointmentCount())
                .thenReturn(rows);

        // Act
        List<CustomerSummary> result = customerService.searchSummary("   ");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).getAppointmentCount());

        verify(customerRepository).findAllWithAppointmentCount();
    }

    @Test
    void adjustLoyaltyStamps_DecrementsTotalVisitsWhenDeltaIsNegative() {
        // Arrange
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setLoyaltyStamps(5);
        customer.setTotalCompletedVisits(10);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Customer updated = customerService.adjustLoyaltyStamps(1L, -1);

        // Assert
        assertEquals(4, updated.getLoyaltyStamps());
        assertEquals(9, updated.getTotalCompletedVisits());
    }

    @Test
    void syncCustomerVisits_RecalculatesVisitsFromCompletedAppointments() {
        // Arrange
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setTotalCompletedVisits(17); // Stale value

        Appointment apt1 = Appointment.builder()
                .status(AppointmentStatus.COMPLETED)
                .build();

        Appointment apt2 = Appointment.builder()
                .status(AppointmentStatus.CANCELLED)
                .build();

        customer.setAppointments(Arrays.asList(apt1, apt2));

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.findByIdWithAppointments(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Customer synced = customerService.syncCustomerVisits(1L);

        // Assert
        assertEquals(1, synced.getTotalCompletedVisits());
    }
}
