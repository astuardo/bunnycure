package cl.bunnycure.service;

import cl.bunnycure.config.WhatsAppConfig;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.BookingRequest;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.ServiceCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppServiceTest {

    @Mock
    private WhatsAppConfig config;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AppSettingsService appSettingsService;

    @Mock
    private CalendarService calendarService;

    @Mock
    private NotificationLogService notificationLogService;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Mock
    private WhatsAppHandoffService whatsAppHandoffService;

    @Mock
    private LoyaltyRewardService loyaltyRewardService;

    @Captor
    private ArgumentCaptor<HttpEntity<Map<String, Object>>> requestCaptor;

    @Captor
    private ArgumentCaptor<String> urlCaptor;

    private WhatsAppService whatsAppService;

    @BeforeEach
    void setUp() {
        whatsAppService = new WhatsAppService(config, restTemplate, appSettingsService, calendarService, notificationLogService, objectMapper, whatsAppHandoffService, loyaltyRewardService);
    }

    @Test
    void sendTextMessage_Success() {
        // Arrange
        String phone = "+56912345678";
        String message = "Test message";
        when(config.getToken()).thenReturn("test-token");
        when(config.getPhoneId()).thenReturn("123456789");
        
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("{\"success\":true}", HttpStatus.OK));

        // Act
        whatsAppService.sendTextMessage(phone, message);

        // Assert
        verify(restTemplate, timeout(1000)).exchange(
                urlCaptor.capture(),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                eq(String.class)
        );

        assertTrue(urlCaptor.getValue().contains("123456789"));
        
        HttpEntity<Map<String, Object>> request = requestCaptor.getValue();
        Map<String, Object> body = request.getBody();
        assertNotNull(body);
        assertEquals("whatsapp", body.get("messaging_product"));
        assertEquals("56912345678", body.get("to")); // Sin el +
        assertEquals("text", body.get("type"));
    }

    @Test
    void sendTextMessage_SkipWhenTokenNotConfigured() {
        // Arrange
        when(config.getToken()).thenReturn(null);

        // Act
        whatsAppService.sendTextMessage("+56912345678", "Test");

        // Assert
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }

    @Test
    void sendTextMessage_SkipWhenPhoneIdNotConfigured() {
        // Arrange
        when(config.getToken()).thenReturn("test-token");
        when(config.getPhoneId()).thenReturn("");

        // Act
        whatsAppService.sendTextMessage("+56912345678", "Test");

        // Assert
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }

    @Test
    void sendAppointmentConfirmation_Success() {
        // Arrange
        Appointment appointment = createTestAppointment();
        when(appSettingsService.getAppJavaLocale()).thenReturn(java.util.Locale.forLanguageTag("es-CL"));
        when(config.isUseTemplateForConfirmation()).thenReturn(false);
        when(config.getToken()).thenReturn("test-token");
        when(config.getPhoneId()).thenReturn("123456789");
        
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("{\"success\":true}", HttpStatus.OK));

        // Act
        whatsAppService.sendAppointmentConfirmation(appointment);

        // Assert - esperar un poco para el async
        verify(restTemplate, timeout(1000)).exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    @Test
    void sendAppointmentConfirmation_SkipWhenNoPhone() {
        // Arrange
        Appointment appointment = createTestAppointment();
        appointment.getCustomer().setPhone(null);

        // Act
        whatsAppService.sendAppointmentConfirmation(appointment);

        // Assert
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }

    @Test
    void normalizePhoneNumber_RemovesSpecialCharacters() {
        // Este método es privado, pero podemos probarlo indirectamente
        when(config.getToken()).thenReturn("test-token");
        when(config.getPhoneId()).thenReturn("123456789");
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("{\"success\":true}", HttpStatus.OK));

        // Act
        whatsAppService.sendTextMessage("+56 9 1234-5678", "Test");

        // Assert
        verify(restTemplate, timeout(1000)).exchange(
                anyString(),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                eq(String.class)
        );

        Map<String, Object> body = requestCaptor.getValue().getBody();
        assertEquals("56912345678", body.get("to"));
    }

    @Test
    void normalizePhoneNumber_AddsChileCountryCode() {
        when(config.getToken()).thenReturn("test-token");
        when(config.getPhoneId()).thenReturn("123456789");
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("{\"success\":true}", HttpStatus.OK));

        // Act - número sin código de país
        whatsAppService.sendTextMessage("912345678", "Test");

        // Assert
        verify(restTemplate, timeout(1000)).exchange(
                anyString(),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                eq(String.class)
        );

        Map<String, Object> body = requestCaptor.getValue().getBody();
        assertEquals("56912345678", body.get("to"));
    }

    @Test
    void sendBookingRequestReceived_Success() {
        // Arrange
        BookingRequest request = createTestBookingRequest();
        when(appSettingsService.getAppJavaLocale()).thenReturn(java.util.Locale.forLanguageTag("es-CL"));
        when(config.getToken()).thenReturn("test-token");
        when(config.getPhoneId()).thenReturn("123456789");
        
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("{\"success\":true}", HttpStatus.OK));

        // Act
        whatsAppService.sendBookingRequestReceived(request);

        // Assert
        verify(restTemplate, timeout(1000)).exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    @Test
    void sendAdminBookingAlertSync_UsesPersonalizedTextByDefault() {
        BookingRequest request = createTestBookingRequest();
        when(appSettingsService.getAppJavaLocale()).thenReturn(java.util.Locale.forLanguageTag("es-CL"));
        when(config.getToken()).thenReturn("test-token");
        when(config.getPhoneId()).thenReturn("123456789");
        when(config.isUseTemplateForAdminAlert()).thenReturn(false);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("{\"success\":true}", HttpStatus.OK));

        boolean sent = whatsAppService.sendAdminBookingAlertSync("+56964499995", request);

        assertTrue(sent);
        verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                eq(String.class)
        );
        Map<String, Object> payload = requestCaptor.getValue().getBody();
        assertNotNull(payload);
        assertEquals("text", payload.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, String> textPayload = (Map<String, String>) payload.get("text");
        assertNotNull(textPayload);
        assertTrue(textPayload.get("body").contains("Ana López solicitó una hora"));
        assertTrue(textPayload.get("body").contains("Revisar ahora"));
    }

    @Test
    void sendAdminBookingAlertSync_WhenTextFails_UsesCustomerTemplateAsFallback() {
        BookingRequest request = createTestBookingRequest();
        when(appSettingsService.getAppJavaLocale()).thenReturn(java.util.Locale.forLanguageTag("es-CL"));
        when(config.getToken()).thenReturn("test-token");
        when(config.getPhoneId()).thenReturn("123456789");
        when(config.isUseTemplateForAdminAlert()).thenReturn(false);
        when(config.isUseTemplateForBookingRequest()).thenReturn(true);
        when(config.getAgendaEnRevisionTemplateName()).thenReturn("agenda_en_revision");
        when(config.getCitaConfirmadaLanguageCode()).thenReturn("es_CL");

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        ))
                .thenThrow(new RuntimeException("text failed"))
                .thenReturn(new ResponseEntity<>("{\"success\":true}", HttpStatus.OK));

        boolean sent = whatsAppService.sendAdminBookingAlertSync("+56964499995", request);

        assertTrue(sent);
        verify(restTemplate, times(2)).exchange(
                anyString(),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                eq(String.class)
        );

        List<HttpEntity<Map<String, Object>>> calls = requestCaptor.getAllValues();
        assertEquals("text", calls.get(0).getBody().get("type"));
        assertEquals("template", calls.get(1).getBody().get("type"));
    }

    @Test
    void sendValoracionServicioGoogleTemplate_Success() {
        // Arrange
        Appointment appointment = createTestAppointment();
        when(config.isUseTemplateForReview()).thenReturn(true);
        when(config.getReviewTemplateName()).thenReturn("valoracion_servicio_google");
        when(config.getCitaConfirmadaLanguageCode()).thenReturn("es_CL");
        when(config.getToken()).thenReturn("test-token");
        when(config.getPhoneId()).thenReturn("123456789");

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("{\"messages\":[{\"id\":\"wamid.HBgL\"}]}", HttpStatus.OK));

        // Act
        boolean sent = whatsAppService.sendValoracionServicioGoogleTemplateSync(appointment);

        // Assert
        assertTrue(sent);
        verify(restTemplate).exchange(
                eq("https://graph.facebook.com/v22.0/123456789/messages"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        );

        Map<String, Object> body = requestCaptor.getValue().getBody();
        assertNotNull(body);
        assertEquals("56912345678", body.get("to"));
        assertEquals("template", body.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> template = (Map<String, Object>) body.get("template");
        assertEquals("valoracion_servicio_google", template.get("name"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) template.get("components");
        assertNotNull(components);
        assertEquals(1, components.size());
        assertEquals("body", components.get(0).get("type"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> params = (List<Map<String, String>>) components.get(0).get("parameters");
        assertEquals(2, params.size());
        assertEquals("María González", params.get(0).get("text"));
        assertEquals("Manicure Clásica", params.get(1).get("text"));
    }

    @Test
    void sendValoracionServicioGoogleTemplate_SkipWhenDisabled() {
        // Arrange
        Appointment appointment = createTestAppointment();
        when(config.isUseTemplateForReview()).thenReturn(false);

        // Act
        boolean sent = whatsAppService.sendValoracionServicioGoogleTemplateSync(appointment);

        // Assert
        assertFalse(sent);
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }

    @Test
    void sendValoracionServicioGoogleTemplate_SkipWhenNoPhone() {
        // Arrange
        Appointment appointment = createTestAppointment();
        appointment.getCustomer().setPhone(null);
        when(config.isUseTemplateForReview()).thenReturn(true);

        // Act
        boolean sent = whatsAppService.sendValoracionServicioGoogleTemplateSync(appointment);

        // Assert
        assertFalse(sent);
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }

    @Test
    void fetchMessageTemplates_Success() throws Exception {
        when(config.getBusinessAccountId()).thenReturn("1449551576874115");
        when(config.getToken()).thenReturn("test-token");

        String jsonResponse = "{\"data\":[{\"name\":\"recordatorio_cita\",\"status\":\"APPROVED\"}]}";
        com.fasterxml.jackson.databind.ObjectMapper realMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode rootNode = realMapper.readTree(jsonResponse);
        when(objectMapper.readTree(jsonResponse)).thenReturn(rootNode);

        when(restTemplate.exchange(
                eq("https://graph.facebook.com/v22.0/1449551576874115/message_templates"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>(jsonResponse, HttpStatus.OK));

        var result = whatsAppService.fetchMessageTemplates();

        assertTrue(result.isPresent());
        assertEquals("recordatorio_cita", result.get().path("data").get(0).path("name").asText());
    }

    @Test
    void fetchMessageTemplates_NoToken_ReturnsEmpty() {
        when(config.getBusinessAccountId()).thenReturn("1449551576874115");
        when(config.getToken()).thenReturn(null);

        var result = whatsAppService.fetchMessageTemplates();

        assertTrue(result.isEmpty());
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    void markMessageAsRead_Success() {
        when(config.getToken()).thenReturn("test-token");
        when(config.getPhoneId()).thenReturn("phone-123");

        when(restTemplate.exchange(
                eq("https://graph.facebook.com/v22.0/phone-123/messages"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("{\"success\":true}", HttpStatus.OK));

        boolean result = whatsAppService.markMessageAsReadSync("wamid.123");
        assertTrue(result);
    }

    @Test
    void fetchPhoneNumberHealth_Success() throws Exception {
        when(config.getToken()).thenReturn("test-token");
        when(config.getPhoneId()).thenReturn("phone-123");

        String jsonResponse = "{\"id\":\"phone-123\",\"quality_rating\":\"GREEN\",\"messaging_limit_tier\":\"TIER_1K\"}";
        com.fasterxml.jackson.databind.ObjectMapper realMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        when(objectMapper.readTree(jsonResponse)).thenReturn(realMapper.readTree(jsonResponse));

        when(restTemplate.exchange(
                contains("phone-123?fields="),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>(jsonResponse, HttpStatus.OK));

        var health = whatsAppService.fetchPhoneNumberHealth();
        assertTrue(health.isPresent());
        assertEquals("GREEN", health.get().path("quality_rating").asText());
    }

    @Test
    void fetchBusinessProfile_Success() throws Exception {
        when(config.getToken()).thenReturn("test-token");
        when(config.getPhoneId()).thenReturn("phone-123");

        String jsonResponse = "{\"data\":[{\"about\":\"BunnyCure\",\"address\":\"Providencia\"}]}";
        com.fasterxml.jackson.databind.ObjectMapper realMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        when(objectMapper.readTree(jsonResponse)).thenReturn(realMapper.readTree(jsonResponse));

        when(restTemplate.exchange(
                contains("phone-123/whatsapp_business_profile"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>(jsonResponse, HttpStatus.OK));

        var profile = whatsAppService.fetchBusinessProfile();
        assertTrue(profile.isPresent());
    }

    @Test
    void updateBusinessProfile_Success() {
        when(config.getToken()).thenReturn("test-token");
        when(config.getPhoneId()).thenReturn("phone-123");

        when(restTemplate.exchange(
                contains("phone-123/whatsapp_business_profile"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("{\"success\":true}", HttpStatus.OK));

        boolean updated = whatsAppService.updateBusinessProfile(Map.of("about", "Nuevo About"));
        assertTrue(updated);
    }

    @Test
    void sendDocumentMessage_Success() {
        when(config.getToken()).thenReturn("test-token");
        when(config.getPhoneId()).thenReturn("phone-123");

        when(restTemplate.exchange(
                eq("https://graph.facebook.com/v22.0/phone-123/messages"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("{\"messages\":[{\"id\":\"wamid-doc\"}]}", HttpStatus.OK));

        boolean sent = whatsAppService.sendDocumentMessage("+56912345678", "https://bunnycure.cl/boleta.pdf", "boleta.pdf", "Tu boleta");
        assertTrue(sent);
    }

    @Test
    void sendImageMessage_Success() {
        when(config.getToken()).thenReturn("test-token");
        when(config.getPhoneId()).thenReturn("phone-123");

        when(restTemplate.exchange(
                eq("https://graph.facebook.com/v22.0/phone-123/messages"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("{\"messages\":[{\"id\":\"wamid-img\"}]}", HttpStatus.OK));

        boolean sent = whatsAppService.sendImageMessage("+56912345678", "https://bunnycure.cl/nail.jpg", "Diseño");
        assertTrue(sent);
    }

    @Test
    void sendInteractiveButtonMessage_Success() {
        when(config.getToken()).thenReturn("test-token");
        when(config.getPhoneId()).thenReturn("phone-123");

        when(restTemplate.exchange(
                eq("https://graph.facebook.com/v22.0/phone-123/messages"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("{\"messages\":[{\"id\":\"wamid-btn\"}]}", HttpStatus.OK));

        boolean sent = whatsAppService.sendInteractiveButtonMessage(
                "+56912345678",
                "Elige una opción",
                List.of(Map.of("id", "btn_1", "title", "Opción 1"))
        );
        assertTrue(sent);
    }

    @Test
    void sendInteractiveListMessage_Success() {
        when(config.getToken()).thenReturn("test-token");
        when(config.getPhoneId()).thenReturn("phone-123");

        when(restTemplate.exchange(
                eq("https://graph.facebook.com/v22.0/phone-123/messages"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("{\"messages\":[{\"id\":\"wamid-list\"}]}", HttpStatus.OK));

        boolean sent = whatsAppService.sendInteractiveListMessage(
                "+56912345678",
                "Servicios BunnyCure",
                "Selecciona uno de nuestros servicios:",
                "BunnyCure 2026",
                "Ver Servicios",
                List.of(Map.of("title", "Manicura", "rows", List.of(Map.of("id", "srv_1", "title", "Rusa"))))
        );
        assertTrue(sent);
    }

    // Métodos auxiliares para crear objetos de prueba

    private Appointment createTestAppointment() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("María González");
        customer.setEmail("maria@example.com");
        customer.setPhone("+56912345678");

        ServiceCatalog service = new ServiceCatalog();
        service.setId(1L);
        service.setName("Manicure Clásica");
        service.setDurationMinutes(60);

        Appointment appointment = Appointment.builder()
                .id(1L)
                .customer(customer)
                .service(service)
                .appointmentDate(LocalDate.of(2026, 3, 10))
                .appointmentTime(LocalTime.of(14, 0))
                .build();

        return appointment;
    }

    private BookingRequest createTestBookingRequest() {
        ServiceCatalog service = new ServiceCatalog();
        service.setId(1L);
        service.setName("Manicure Clásica");
        service.setDurationMinutes(60);

        BookingRequest request = new BookingRequest();
        request.setId(1L);
        request.setFullName("Ana López");
        request.setEmail("ana@example.com");
        request.setPhone("+56987654321");
        request.setService(service);
        request.setPreferredDate(LocalDate.of(2026, 3, 15));
        request.setPreferredBlock("MORNING");

        return request;
    }
}
