package cl.bunnycure.web.controller;

import cl.bunnycure.config.TestMailConfiguration;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.repository.CustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestMailConfiguration.class)
class CustomerApiControllerRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    private Long createdCustomerId;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer();
        customer.setTenantId(1L);
        customer.setPublicId("test-lookup-uuid-123");
        customer.setFullName("Valentina Flores");
        customer.setPhone("+56977665544");
        customer.setEmail("valentina@test.cl");
        customer.setRut("19.876.543-2");
        customer.setHealthNotes("Alergia a productos acrílicos");
        customer.setEmergencyPhone("+56911223344");

        Customer saved = customerRepository.save(customer);
        createdCustomerId = saved.getId();
    }

    @AfterEach
    void tearDown() {
        if (createdCustomerId != null) {
            customerRepository.deleteById(createdCustomerId);
        }
    }

    @Test
    @DisplayName("Lookup público no debe exponer notas de salud ni teléfonos de emergencia")
    void lookup_ReturnsSanitizedDataWithoutExposingHealthNotes() throws Exception {
        mockMvc.perform(post("/api/customers/lookup")
                        .param("phone", "+56977665544")
                        .header("X-Forwarded-For", "198.51.100.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found", is(true)))
                .andExpect(jsonPath("$.fullName", is("Valentina Flores")))
                .andExpect(jsonPath("$.phone", is("+56977665544")))
                .andExpect(jsonPath("$.email", is("valentina@test.cl")))
                .andExpect(jsonPath("$.healthNotes").doesNotExist())
                .andExpect(jsonPath("$.emergencyPhone").doesNotExist());
    }

    @Test
    @DisplayName("Lookup público bloquea con 429 Too Many Requests tras superar límite de tasa")
    void lookup_WhenRateLimitExceeded_Returns429TooManyRequests() throws Exception {
        String testIp = "203.0.113.88";

        // Realizar 15 peticiones permitidas
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(post("/api/customers/lookup")
                            .param("phone", "+56977665544")
                            .header("X-Forwarded-For", testIp))
                    .andExpect(status().isOk());
        }

        // La petición 16 debe ser rechazada con HTTP 429 Too Many Requests
        mockMvc.perform(post("/api/customers/lookup")
                        .param("phone", "+56977665544")
                        .header("X-Forwarded-For", testIp))
                .andExpect(status().isTooManyRequests());
    }
}
