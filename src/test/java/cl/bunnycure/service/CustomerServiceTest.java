package cl.bunnycure.service;

import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.web.dto.CustomerSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

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
    void searchSummary_CallsAggregatedQueryWithoutNPlusOne() {
        // Arrange
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Francisca Morales");
        customer.setPhone("+56911223344");

        Object[] row = new Object[]{customer, 5L};
        List<Object[]> rows = Collections.singletonList(row);
        when(customerRepository.searchWithAppointmentCount("Francisca"))
                .thenReturn(rows);

        // Act
        List<CustomerSummary> result = customerService.searchSummary("Francisca");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Francisca Morales", result.get(0).getCustomer().getFullName());
        assertEquals(5L, result.get(0).getAppointmentCount());

        verify(customerRepository).searchWithAppointmentCount("Francisca");
        verify(customerRepository, never()).countAppointmentsByCustomerId(any());
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
        verify(customerRepository, never()).searchWithAppointmentCount(any());
    }
}
