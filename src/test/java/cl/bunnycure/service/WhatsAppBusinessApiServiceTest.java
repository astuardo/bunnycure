package cl.bunnycure.service;

import cl.bunnycure.config.WhatsAppConfig;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** API de gestión de WhatsApp Business (trasladados desde WhatsAppServiceTest). */
@ExtendWith(MockitoExtension.class)
class WhatsAppBusinessApiServiceTest {

    @Mock
    private WhatsAppConfig config;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private WhatsAppBusinessApiService businessApiService;

    @BeforeEach
    void setUp() {
        businessApiService = new WhatsAppBusinessApiService(config, restTemplate, objectMapper);
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

        var result = businessApiService.fetchMessageTemplates();

        assertTrue(result.isPresent());
        assertEquals("recordatorio_cita", result.get().path("data").get(0).path("name").asText());
    }

    @Test
    void fetchMessageTemplates_NoToken_ReturnsEmpty() {
        when(config.getBusinessAccountId()).thenReturn("1449551576874115");
        when(config.getToken()).thenReturn(null);

        var result = businessApiService.fetchMessageTemplates();

        assertTrue(result.isEmpty());
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
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

        var health = businessApiService.fetchPhoneNumberHealth();
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

        var profile = businessApiService.fetchBusinessProfile();
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

        boolean updated = businessApiService.updateBusinessProfile(Map.of("about", "Nuevo About"));
        assertTrue(updated);
    }
}
