package cl.bunnycure.service;

import cl.bunnycure.config.ApiGatewayConfig;
import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.InvoiceLog;
import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.domain.repository.InvoiceLogRepository;
import cl.bunnycure.web.dto.InvoiceContrastResultDto;
import cl.bunnycure.web.dto.InvoiceIssuedItemDto;
import cl.bunnycure.web.dto.InvoicePendingAppointmentDto;
import cl.bunnycure.web.dto.InvoiceSummaryDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
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
    private AppointmentRepository appointmentRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ApiGatewaySiiService service;

    @BeforeEach
    void setUp() {
        service = new ApiGatewaySiiService(
                config,
                invoiceLogRepository,
                appointmentRepository,
                customerRepository,
                restTemplate,
                objectMapper
        );
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
        verify(invoiceLogRepository).save(any(InvoiceLog.class));
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
        verify(invoiceLogRepository, atLeastOnce()).save(any(InvoiceLog.class));
    }

    @Test
    void generateInvoice_RetryWhenPreviousFailed_EmitsSuccessfully() {
        when(config.isConfigured()).thenReturn(true);
        when(config.getEndpoint()).thenReturn("https://app.apigateway.cl");
        when(config.getToken()).thenReturn("test-token");
        when(config.getSiiRut()).thenReturn("76123456-7");
        when(config.getSiiPassword()).thenReturn("secret123");
        when(config.isSendEmailOnIssue()).thenReturn(false);

        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Valentina Soto");
        customer.setRut("18.664.589-8");

        Appointment appointment = Appointment.builder().id(100L).customer(customer).build();
        InvoiceLog failedLog = InvoiceLog.builder()
                .id(1L)
                .appointment(appointment)
                .customer(customer)
                .status("FAILED")
                .errorMessage("RUT inválido previo")
                .build();

        // El log anterior falló, ahora debe permitir el reintento
        when(invoiceLogRepository.findByAppointmentId(100L)).thenReturn(Optional.of(failedLog));

        String emitirResponseJson = """
                {
                    "folio": "12346",
                    "codigo": "BHE-ABC-790"
                }
                """;

        when(restTemplate.postForEntity(eq("https://app.apigateway.cl/api/v2/sii/bhe/emitidas/emitir"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(emitirResponseJson, HttpStatus.OK));

        when(invoiceLogRepository.save(any(InvoiceLog.class))).thenReturn(failedLog);

        Optional<String> result = service.generateInvoice(appointment, customer, BigDecimal.valueOf(20000));

        assertTrue(result.isPresent());
        assertEquals("12346", result.get());
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
        verify(invoiceLogRepository).save(any(InvoiceLog.class));
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

    @Test
    void emitInvoiceForAppointment_Success() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Francisca Mena");
        customer.setRut("18.664.589-8");
        customer.setEmail("francisca@example.com");

        ServiceCatalog serviceItem = ServiceCatalog.builder().id(10L).name("Esmaltado Permanente").price(BigDecimal.valueOf(18000)).build();
        Appointment appointment = Appointment.builder()
                .id(200L)
                .customer(customer)
                .service(serviceItem)
                .services(List.of(serviceItem))
                .status(AppointmentStatus.COMPLETED)
                .build();

        when(appointmentRepository.findByIdWithDetails(200L)).thenReturn(Optional.of(appointment));

        when(config.isConfigured()).thenReturn(true);
        when(config.getEndpoint()).thenReturn("https://app.apigateway.cl");
        when(config.getToken()).thenReturn("token");
        when(config.getSiiRut()).thenReturn("76123456-7");
        when(config.getSiiPassword()).thenReturn("pass");

        String emitirResponseJson = """
                {
                    "folio": "9999",
                    "codigo": "BHE-9999"
                }
                """;
        when(restTemplate.postForEntity(eq("https://app.apigateway.cl/api/v2/sii/bhe/emitidas/emitir"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(emitirResponseJson, HttpStatus.OK));

        InvoiceLog successLog = InvoiceLog.builder()
                .id(50L)
                .appointment(appointment)
                .customer(customer)
                .invoiceNumber("9999")
                .siiCode("BHE-9999")
                .amountInClp(BigDecimal.valueOf(18000))
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .build();

        when(invoiceLogRepository.save(any(InvoiceLog.class))).thenReturn(successLog);
        when(invoiceLogRepository.findByAppointmentId(200L)).thenReturn(Optional.empty()).thenReturn(Optional.of(successLog));

        InvoiceIssuedItemDto result = service.emitInvoiceForAppointment(200L, "18.664.589-8", "nueva@example.com");

        assertNotNull(result);
        assertEquals("9999", result.getInvoiceNumber());
        assertEquals("BHE-9999", result.getSiiCode());
    }

    @Test
    void getSummary_ReturnsMetrics() {
        when(invoiceLogRepository.countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq("SUCCESS"), any(), any()))
                .thenReturn(15L);
        when(appointmentRepository.countCompletedAppointmentsWithoutSuccessfulInvoice(AppointmentStatus.COMPLETED))
                .thenReturn(3L);
        when(invoiceLogRepository.countByStatus("FAILED"))
                .thenReturn(2L);
        when(invoiceLogRepository.sumAmountByStatusSuccessAndCreatedAtBetween(any(), any()))
                .thenReturn(BigDecimal.valueOf(350000));
        when(config.isConfigured()).thenReturn(true);
        when(config.getSiiRut()).thenReturn("76123456-7");

        InvoiceSummaryDto summary = service.getSummary();

        assertNotNull(summary);
        assertEquals(15L, summary.getGeneratedThisMonth());
        assertEquals(3L, summary.getPendingInvoicesCount());
        assertEquals(2L, summary.getFailedInvoicesCount());
        assertEquals(BigDecimal.valueOf(350000), summary.getTotalAmountMonth());
        assertTrue(summary.isApiGatewayConfigured());
        assertEquals("76123456-7", summary.getEmisorRut());
    }

    @Test
    void listIssuedInvoices_UsesCacheToSaveCredits() {
        when(config.isConfigured()).thenReturn(true);
        when(config.getEndpoint()).thenReturn("https://app.apigateway.cl");
        when(config.getToken()).thenReturn("token");
        when(config.getSiiRut()).thenReturn("76123456-7");
        when(config.getSiiPassword()).thenReturn("pass");

        String responseJson = "{\"documentos\":[{\"folio\":\"1001\",\"monto\":25000}]}";
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(responseJson, HttpStatus.OK));

        // Primera llamada: consulta a la API
        JsonNode firstCall = service.listIssuedInvoices("202608", 1, false);
        assertNotNull(firstCall);

        // Segunda llamada con forceRefresh = false: DEBE salir de caché sin hacer llamada HTTP
        JsonNode secondCall = service.listIssuedInvoices("202608", 1, false);
        assertNotNull(secondCall);

        // Verifica que la API externa solo se llamó 1 vez
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void markInvoiceAsManual_Success_DoesNotCallSiiApi() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Francisca Mena");
        customer.setRut("18.664.589-8");

        ServiceCatalog serviceItem = ServiceCatalog.builder().id(10L).name("Esmaltado").price(BigDecimal.valueOf(15000)).build();
        Appointment appointment = Appointment.builder()
                .id(300L)
                .customer(customer)
                .service(serviceItem)
                .services(List.of(serviceItem))
                .status(AppointmentStatus.COMPLETED)
                .build();

        when(appointmentRepository.findByIdWithDetails(300L)).thenReturn(Optional.of(appointment));
        when(invoiceLogRepository.findByAppointmentId(300L)).thenReturn(Optional.empty());

        InvoiceLog savedLog = InvoiceLog.builder()
                .id(99L)
                .appointment(appointment)
                .customer(customer)
                .invoiceNumber("1050")
                .siiCode("MANUAL")
                .amountInClp(BigDecimal.valueOf(15000))
                .status("SUCCESS")
                .build();
        when(invoiceLogRepository.save(any(InvoiceLog.class))).thenReturn(savedLog);

        InvoiceIssuedItemDto result = service.markInvoiceAsManual(300L, "1050", "Emitida en sii.cl");

        assertNotNull(result);
        assertEquals("1050", result.getInvoiceNumber());
        assertEquals("MANUAL", result.getSiiCode());
        verifyNoInteractions(restTemplate); // NO llama al SII ni consume créditos
    }

    @Test
    void batchMarkAsManual_AssignsSequentialFolios() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Francisca Mena");

        ServiceCatalog serviceItem = ServiceCatalog.builder().id(10L).name("Esmaltado").price(BigDecimal.valueOf(15000)).build();
        Appointment apt1 = Appointment.builder().id(301L).customer(customer).service(serviceItem).services(List.of(serviceItem)).build();
        Appointment apt2 = Appointment.builder().id(302L).customer(customer).service(serviceItem).services(List.of(serviceItem)).build();

        when(appointmentRepository.findByIdWithDetails(301L)).thenReturn(Optional.of(apt1));
        when(appointmentRepository.findByIdWithDetails(302L)).thenReturn(Optional.of(apt2));

        when(invoiceLogRepository.findByAppointmentId(anyLong())).thenReturn(Optional.empty());
        when(invoiceLogRepository.save(any(InvoiceLog.class))).thenAnswer(inv -> inv.getArgument(0));

        List<InvoiceIssuedItemDto> results = service.batchMarkAsManual(List.of(301L, 302L), "2000", "Lote manual");

        assertEquals(2, results.size());
        assertEquals("2000", results.get(0).getInvoiceNumber());
        assertEquals("2001", results.get(1).getInvoiceNumber());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void extractCodigo_ExtractsCodigoBarrasAsSiiIdentifier() throws Exception {
        String jsonStr = """
                {
                    "data": {
                        "Encabezado": {
                            "IdDoc": {
                                "Folio": 66,
                                "CodigoBarras": "1866971000066525C5FC",
                                "CodigoInferior": "11202608302257"
                            }
                        }
                    }
                }
                """;
        JsonNode json = objectMapper.readTree(jsonStr);

        String codigo = service.extractCodigo(json);
        String barcode = service.extractBarcode(json);

        assertEquals("1866971000066525C5FC", codigo);
        assertEquals("1866971000066525C5FC", barcode);
    }

    @Test
    void getInvoicePdf_ThrowsException_WhenHtmlErrorReceived() {
        when(config.isConfigured()).thenReturn(true);
        when(config.getEndpoint()).thenReturn("https://app.apigateway.cl");
        when(config.getSiiRut()).thenReturn("76123456-7");
        when(config.getSiiPassword()).thenReturn("secret123");

        byte[] htmlError = "<!DOCTYPE html><html><body>No existe la boleta de honorarios electrónica</body></html>"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        when(restTemplate.exchange(eq("https://app.apigateway.cl/api/v2/sii/bhe/emitidas/pdf/INVALID-CODE"), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(htmlError, HttpStatus.OK));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.getInvoicePdf("INVALID-CODE"));
        assertTrue(ex.getMessage().contains("No existe la boleta") || ex.getMessage().contains("no encontró la boleta"));
    }

    @Test
    void generateInvoice_UsesAppointmentDate() {
        when(config.isConfigured()).thenReturn(true);
        when(config.getEndpoint()).thenReturn("https://app.apigateway.cl");
        when(config.getToken()).thenReturn("test-token");
        when(config.getSiiRut()).thenReturn("76123456-7");
        when(config.getSiiPassword()).thenReturn("secret123");
        when(config.isSendEmailOnIssue()).thenReturn(false);

        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Valentina Soto");
        customer.setRut("18.664.589-8");

        LocalDate serviceDate = LocalDate.of(2026, 8, 20);
        Appointment appointment = Appointment.builder()
                .id(100L)
                .customer(customer)
                .appointmentDate(serviceDate)
                .build();

        when(invoiceLogRepository.findByAppointmentId(100L)).thenReturn(Optional.empty());

        org.mockito.ArgumentCaptor<HttpEntity<String>> entityCaptor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.postForEntity(eq("https://app.apigateway.cl/api/v2/sii/bhe/emitidas/emitir"), entityCaptor.capture(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"folio\":\"50\",\"codigo\":\"BHE-50\"}", HttpStatus.OK));

        when(invoiceLogRepository.save(any(InvoiceLog.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<String> folio = service.generateInvoice(appointment, customer, BigDecimal.valueOf(30000));

        assertTrue(folio.isPresent());
        assertEquals("50", folio.get());

        // Verificar que el payload contiene la fecha del servicio 2026-08-20
        HttpEntity<String> capturedEntity = entityCaptor.getValue();
        assertNotNull(capturedEntity);
        assertTrue(capturedEntity.getBody().contains("\"FchEmis\":\"2026-08-20\""));
    }
}

