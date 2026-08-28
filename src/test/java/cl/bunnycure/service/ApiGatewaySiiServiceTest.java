package cl.bunnycure.service;

import cl.bunnycure.config.ApiGatewayConfig;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.InvoiceLog;
import cl.bunnycure.domain.repository.InvoiceLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiGatewaySiiServiceTest {

    @Mock
    private ApiGatewayConfig config;

    @Mock
    private InvoiceLogRepository invoiceLogRepository;

    @Mock
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ApiGatewaySiiService service;

    @BeforeEach
    void setUp() {
        service = new ApiGatewaySiiService(config, invoiceLogRepository, restTemplate, objectMapper);
    }

    @Test
    void validateRutFormat_ValidRuts() {
        assertTrue(service.validateRutFormat("18.664.589-8"));
        assertTrue(service.validateRutFormat("18664589-8"));
        assertTrue(service.validateRutFormat("11.111.111-1"));
        assertTrue(service.validateRutFormat("11.111.112-K"));
        assertTrue(service.validateRutFormat("7.654.321-6"));
    }


    @Test
    void validateRutFormat_InvalidRuts() {
        assertFalse(service.validateRutFormat("18.664.589-0")); // DV erróneo
        assertFalse(service.validateRutFormat("invalid"));
        assertFalse(service.validateRutFormat(""));
        assertFalse(service.validateRutFormat(null));
    }

    @Test
    void sanitizeRut_FormatsProperly() {
        assertEquals("18664589-8", service.sanitizeRut("18.664.589-8"));
        assertEquals("18664589-8", service.sanitizeRut("186645898"));
        assertEquals("7654321-K", service.sanitizeRut("7.654.321-k"));
    }

    @Test
    void generateInvoice_DryRun_WhenNotConfigured() {
        when(config.isConfigured()).thenReturn(false);

        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Valentina Soto");
        customer.setRut("18.664.589-8");
        customer.setEmail("valentina@example.com");

        Appointment appointment = Appointment.builder().id(100L).customer(customer).build();
        when(invoiceLogRepository.findByAppointmentId(100L)).thenReturn(Optional.empty());

        Optional<String> result = service.generateInvoice(appointment, customer, BigDecimal.valueOf(25000));

        assertTrue(result.isEmpty());
        verify(invoiceLogRepository).save(argThat(log -> "FAILED".equals(log.getStatus())));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void generateInvoice_Success_EmitsAndSendsEmail() {
        when(config.isConfigured()).thenReturn(true);
        when(config.getEndpoint()).thenReturn("https://app.apigateway.cl");
        when(config.getToken()).thenReturn("test-token");
        when(config.getSiiRut()).thenReturn("76123456-7");
        when(config.getSiiPassword()).thenReturn("secret123");
        when(config.isSendEmailOnIssue()).thenReturn(true);

        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Valentina Soto");
        customer.setRut("18.664.589-8");
        customer.setEmail("valentina@example.com");

        Appointment appointment = Appointment.builder().id(100L).customer(customer).build();
        when(invoiceLogRepository.findByAppointmentId(100L)).thenReturn(Optional.empty());

        String emitirResponseJson = """
                {
                    "folio": "12345",
                    "codigo": "BHE-ABC-789",
                    "codigo_barras": "1234567890",
                    "fecha_emision": "2026-08-27"
                }
                """;

        when(restTemplate.postForEntity(eq("https://app.apigateway.cl/api/v2/sii/bhe/emitidas/emitir"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(emitirResponseJson, HttpStatus.OK));

        when(restTemplate.postForEntity(eq("https://app.apigateway.cl/api/v2/sii/bhe/emitidas/email/BHE-ABC-789"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"success\":true}", HttpStatus.OK));

        InvoiceLog mockSavedLog = InvoiceLog.builder()
                .id(1L)
                .appointment(appointment)
                .customer(customer)
                .invoiceNumber("12345")
                .siiCode("BHE-ABC-789")
                .status("SUCCESS")
                .build();

        when(invoiceLogRepository.save(any(InvoiceLog.class))).thenReturn(mockSavedLog);

        Optional<String> folioResult = service.generateInvoice(appointment, customer, BigDecimal.valueOf(25000));

        assertTrue(folioResult.isPresent());
        assertEquals("12345", folioResult.get());

        // Verificar que se guardó el log de éxito y el envío de email
        verify(invoiceLogRepository, atLeastOnce()).save(any(InvoiceLog.class));
    }

    @Test
    void generateInvoice_InvalidRut_ReturnsEmpty() {
        when(config.isConfigured()).thenReturn(true);

        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Valentina Soto");
        customer.setRut("invalid-rut");

        Appointment appointment = Appointment.builder().id(100L).customer(customer).build();
        when(invoiceLogRepository.findByAppointmentId(100L)).thenReturn(Optional.empty());

        Optional<String> result = service.generateInvoice(appointment, customer, BigDecimal.valueOf(25000));

        assertTrue(result.isEmpty());
        verify(invoiceLogRepository).save(argThat(log -> "FAILED".equals(log.getStatus())));
        verifyNoInteractions(restTemplate);
    }


    @Test
    void getInvoicePdf_Success() {
        when(config.isConfigured()).thenReturn(true);
        when(config.getEndpoint()).thenReturn("https://app.apigateway.cl");
        when(config.getSiiRut()).thenReturn("76123456-7");
        when(config.getSiiPassword()).thenReturn("secret123");

        byte[] fakePdf = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF
        when(restTemplate.exchange(eq("https://app.apigateway.cl/api/v2/sii/bhe/emitidas/pdf/COD123"), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(fakePdf, HttpStatus.OK));

        byte[] pdf = service.getInvoicePdf("COD123");

        assertNotNull(pdf);
        assertArrayEquals(fakePdf, pdf);
    }
}
