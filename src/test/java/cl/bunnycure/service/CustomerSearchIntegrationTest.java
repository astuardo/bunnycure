package cl.bunnycure.service;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.config.TestMailConfiguration;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.web.dto.CustomerSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestMailConfiguration.class)
@DisplayName("Búsqueda paginada de clientas: tolerante a tildes, orden, RUT y teléfono")
class CustomerSearchIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> createdIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(1L);
        createdIds.add(save("María José Pérez", "+56970000005", "15.007.919-0"));
        createdIds.add(save("Camila Soto", "+56970000006", "15.015.838-K"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.setCurrentTenantId(1L);
        transactionTemplate.execute(status -> {
            createdIds.forEach(customerRepository::deleteById);
            return null;
        });
        TenantContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"maria", "María", "perez", "PÉREZ maría", "jose perez", "15007919", "15.007.919", "9 7000 0005", "70000005"})
    void findsMariaRegardlessOfAccentsOrderOrFormat(String query) {
        List<String> names = search(query).getContent().stream().map(s -> s.getFullName()).toList();

        assertTrue(names.contains("María José Pérez"), () -> "'" + query + "' no encontró a María: " + names);
        assertTrue(!names.contains("Camila Soto"), () -> "'" + query + "' trajo resultados ajenos: " + names);
    }

    @Test
    void wordsInAnyOrderMustAllMatch() {
        assertEquals(1, search("soto camila").getTotalElements());
        assertEquals(0, search("camila perez").getTotalElements());
    }

    @Test
    void searchTextIsRefreshedWhenTheCustomerChanges() {
        Long id = createdIds.get(1);
        transactionTemplate.execute(status -> {
            Customer c = customerRepository.findById(id).orElseThrow();
            c.setFullName("Camila Ñúñez");
            return customerRepository.save(c);
        });

        assertEquals(1, search("nunez").getTotalElements());
    }

    private Page<CustomerSummary> search(String query) {
        return transactionTemplate.execute(status -> customerService.searchSummary(query, PageRequest.of(0, 50)));
    }

    private Long save(String name, String phone, String rut) {
        return transactionTemplate.execute(status -> customerRepository.save(new Customer(name, phone, null, rut)).getId());
    }
}
