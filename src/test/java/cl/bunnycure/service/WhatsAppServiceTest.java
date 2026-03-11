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

    @Captor
    private ArgumentCaptor<HttpEntity<Map<String, Object>>> requestCaptor;

    @Captor
    private ArgumentCaptor<String> urlCaptor;

    private WhatsAppService whatsAppService;

    @BeforeEach
    void setUp() {
        whatsAppService = new WhatsAppService(config, restTemplate);
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
