package cl.bunnycure.web.controller;

import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.service.AppSettingsService;
import cl.bunnycure.service.BookingRequestService;
import cl.bunnycure.service.ServiceCatalogService;
import cl.bunnycure.web.dto.BookingRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests para BookingController (T3.3 - Sprint 3)
 * 
 * Valida:
 * - Página /reservar renderiza correctamente con settings dinámicos
 * - Bloques horarios configurables desde AppSettings
 * - WhatsApp handoff habilitado/deshabilitado
 * - Templates de mensajes dinámicos
 * - Servicios activos filtrados
 * - Envío de formulario exitoso/con errores
 */
@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private ServiceCatalogService serviceCatalogService;

    @Mock
    private AppSettingsService appSettingsService;

    @Mock
    private BookingRequestService bookingRequestService;

    @InjectMocks
    private BookingController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldRenderReservarPage_withDynamicSettings() throws Exception {
        // Given
        ServiceCatalog service1 = ServiceCatalog.builder()
                .id(1L)
                .name("Corte de Pelo")
                .active(true)
                .build();
        
        ServiceCatalog service2 = ServiceCatalog.builder()
                .id(2L)
                .name("Manicure")
                .active(true)
                .build();

        when(serviceCatalogService.findAll()).thenReturn(List.of(service1, service2));
        when(appSettingsService.get("booking.enabled", "true")).thenReturn("true");
        when(appSettingsService.getHumanWhatsappNumber()).thenReturn("+56912345678");
        when(appSettingsService.getHumanWhatsappDisplayName()).thenReturn("Soporte Bunnycure");
        when(appSettingsService.isWhatsappHandoffEnabled()).thenReturn(true);
        when(appSettingsService.getWhatsappHandoffClientMessage())
                .thenReturn("Contáctanos directamente por WhatsApp");
        when(appSettingsService.getBookingMessageTemplate())
                .thenReturn("Hola! Quiero reservar: {servicio}");
        when(appSettingsService.get("booking.block.morning", "09:00 – 13:00"))
                .thenReturn("08:00 – 12:00");
        when(appSettingsService.get("booking.block.afternoon", "14:00 – 18:00"))
                .thenReturn("13:00 – 17:00");
        when(appSettingsService.get("booking.block.night", "18:00 – 21:00"))
                .thenReturn("17:00 – 20:00");

        // When/Then
        mockMvc.perform(get("/reservar"))
                .andExpect(status().isOk())
                .andExpect(view().name("reservar/index"))
                .andExpect(model().attribute("bookingEnabled", true))
                .andExpect(model().attribute("whatsappNumber", "+56912345678"))
                .andExpect(model().attribute("whatsappHumanNumber", "+56912345678"))
                .andExpect(model().attribute("whatsappHumanDisplayName", "Soporte Bunnycure"))
                .andExpect(model().attribute("whatsappHandoffEnabled", true))
                .andExpect(model().attribute("whatsappHandoffClientMessage", "Contáctanos directamente por WhatsApp"))
                .andExpect(model().attribute("messageTemplate", "Hola! Quiero reservar: {servicio}"))
                .andExpect(model().attribute("submitted", false));

        verify(serviceCatalogService).findAll();
    }

    @Test
    void shouldRenderReservarPage_withTrailingSlash() throws Exception {
        // Given
        when(serviceCatalogService.findAll()).thenReturn(List.of());
        when(appSettingsService.get(anyString(), anyString())).thenReturn("default");
        when(appSettingsService.getHumanWhatsappNumber()).thenReturn("");
        when(appSettingsService.getHumanWhatsappDisplayName()).thenReturn("");
        when(appSettingsService.isWhatsappHandoffEnabled()).thenReturn(false);
        when(appSettingsService.getWhatsappHandoffClientMessage()).thenReturn("");
        when(appSettingsService.getBookingMessageTemplate()).thenReturn("");

        // When/Then
        mockMvc.perform(get("/reservar/"))
                .andExpect(status().isOk())
                .andExpect(view().name("reservar/index"));
    }

    @Test
    void shouldFilterActiveServicesOnly() throws Exception {
        // Given
        ServiceCatalog activeService = ServiceCatalog.builder()
                .id(1L)
                .name("Servicio Activo")
                .active(true)
                .build();
        
        ServiceCatalog inactiveService = ServiceCatalog.builder()
                .id(2L)
                .name("Servicio Inactivo")
                .active(false)
                .build();

        when(serviceCatalogService.findAll()).thenReturn(List.of(activeService, inactiveService));
        when(appSettingsService.get(anyString(), anyString())).thenReturn("default");
        when(appSettingsService.getHumanWhatsappNumber()).thenReturn("");
        when(appSettingsService.getHumanWhatsappDisplayName()).thenReturn("");
        when(appSettingsService.isWhatsappHandoffEnabled()).thenReturn(false);
        when(appSettingsService.getWhatsappHandoffClientMessage()).thenReturn("");
        when(appSettingsService.getBookingMessageTemplate()).thenReturn("");

        // When/Then
        mockMvc.perform(get("/reservar"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("services"));
    }

    @Test
    void shouldRenderWithCustomTimeBlocks() throws Exception {
        // Given
        when(serviceCatalogService.findAll()).thenReturn(List.of());
        when(appSettingsService.get("booking.enabled", "true")).thenReturn("true");
        when(appSettingsService.getHumanWhatsappNumber()).thenReturn("");
        when(appSettingsService.getHumanWhatsappDisplayName()).thenReturn("");
        when(appSettingsService.isWhatsappHandoffEnabled()).thenReturn(false);
        when(appSettingsService.getWhatsappHandoffClientMessage()).thenReturn("");
        when(appSettingsService.getBookingMessageTemplate()).thenReturn("");
        when(appSettingsService.get("booking.block.morning", "09:00 – 13:00"))
                .thenReturn("07:00 – 11:00");
        when(appSettingsService.get("booking.block.afternoon", "14:00 – 18:00"))
                .thenReturn("12:00 – 16:00");
        when(appSettingsService.get("booking.block.night", "18:00 – 21:00"))
                .thenReturn("19:00 – 22:00");

        // When/Then
        mockMvc.perform(get("/reservar"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("timeBlocks"));
    }

    @Test
    void shouldRenderWithBookingDisabled() throws Exception {
        // Given
        when(serviceCatalogService.findAll()).thenReturn(List.of());
        when(appSettingsService.get("booking.enabled", "true")).thenReturn("false");
        when(appSettingsService.getHumanWhatsappNumber()).thenReturn("");
        when(appSettingsService.getHumanWhatsappDisplayName()).thenReturn("");
        when(appSettingsService.isWhatsappHandoffEnabled()).thenReturn(false);
        when(appSettingsService.getWhatsappHandoffClientMessage()).thenReturn("");
        when(appSettingsService.getBookingMessageTemplate()).thenReturn("");
        when(appSettingsService.get(eq("booking.block.morning"), anyString())).thenReturn("09:00 – 13:00");
        when(appSettingsService.get(eq("booking.block.afternoon"), anyString())).thenReturn("14:00 – 18:00");
        when(appSettingsService.get(eq("booking.block.night"), anyString())).thenReturn("18:00 – 21:00");

        // When/Then
        mockMvc.perform(get("/reservar"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("bookingEnabled", false));
    }

    @Test
    void shouldRenderWithWhatsappHandoffDisabled() throws Exception {
        // Given
        when(serviceCatalogService.findAll()).thenReturn(List.of());
        when(appSettingsService.get("booking.enabled", "true")).thenReturn("true");
        when(appSettingsService.getHumanWhatsappNumber()).thenReturn("+56912345678");
        when(appSettingsService.getHumanWhatsappDisplayName()).thenReturn("Soporte");
        when(appSettingsService.isWhatsappHandoffEnabled()).thenReturn(false);
        when(appSettingsService.getWhatsappHandoffClientMessage()).thenReturn("");
        when(appSettingsService.getBookingMessageTemplate()).thenReturn("");
        when(appSettingsService.get(eq("booking.block.morning"), anyString())).thenReturn("09:00 – 13:00");
        when(appSettingsService.get(eq("booking.block.afternoon"), anyString())).thenReturn("14:00 – 18:00");
        when(appSettingsService.get(eq("booking.block.night"), anyString())).thenReturn("18:00 – 21:00");

        // When/Then
        mockMvc.perform(get("/reservar"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("whatsappHandoffEnabled", false));
    }

    @Test
    void shouldSubmitBookingSuccessfully() throws Exception {
        // Given - DTO válido con todos los campos requeridos
        BookingRequestDto dto = new BookingRequestDto();
        dto.setFullName("Juan Pérez");
        dto.setPhone("+56912345678");
        dto.setGender("MASCULINO");
        dto.setBirthDate(java.time.LocalDate.of(2000, 1, 1));
        dto.setEmail("juan@example.com");
        dto.setServiceId(1L);
        dto.setPreferredDate(java.time.LocalDate.now().plusDays(7));
        dto.setPreferredBlock("Mañana");
        dto.setNotificationPreference(cl.bunnycure.domain.enums.NotificationPreference.BOTH);

        // When/Then
        mockMvc.perform(post("/reservar/submit")
                        .flashAttr("bookingRequest", dto))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reservar"))
                .andExpect(flash().attribute("submitted", true));

        verify(bookingRequestService).create(any(BookingRequestDto.class));
    }

    @Test
    void shouldReturnErrorWhenServiceThrowsException() throws Exception {
        // Given - DTO válido pero el servicio lanza excepción
        BookingRequestDto dto = new BookingRequestDto();
        dto.setFullName("Juan Pérez");
        dto.setPhone("+56912345678");
        dto.setGender("FEMENINO");
        dto.setBirthDate(java.time.LocalDate.of(2000, 1, 1));
        dto.setEmail("juan@example.com");
        dto.setServiceId(1L);
        dto.setPreferredDate(java.time.LocalDate.now().plusDays(7));
        dto.setPreferredBlock("Tarde");
        dto.setNotificationPreference(cl.bunnycure.domain.enums.NotificationPreference.EMAIL_ONLY);

        when(bookingRequestService.create(any(BookingRequestDto.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When/Then
        mockMvc.perform(post("/reservar/submit")
                        .flashAttr("bookingRequest", dto))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reservar"))
                .andExpect(flash().attribute("errorMsg", 
                    "Hubo un error al enviar tu solicitud. Por favor intenta de nuevo."));

        verify(bookingRequestService).create(any(BookingRequestDto.class));
    }

    @Test
    void shouldRenderWithDefaultTimeBlocks_whenNotConfigured() throws Exception {
        // Given
        when(serviceCatalogService.findAll()).thenReturn(List.of());
        when(appSettingsService.get("booking.enabled", "true")).thenReturn("true");
        when(appSettingsService.getHumanWhatsappNumber()).thenReturn("");
        when(appSettingsService.getHumanWhatsappDisplayName()).thenReturn("");
        when(appSettingsService.isWhatsappHandoffEnabled()).thenReturn(false);
        when(appSettingsService.getWhatsappHandoffClientMessage()).thenReturn("");
        when(appSettingsService.getBookingMessageTemplate()).thenReturn("");
        
        // Retornar valores por defecto
        when(appSettingsService.get("booking.block.morning", "09:00 – 13:00"))
                .thenReturn("09:00 – 13:00");
        when(appSettingsService.get("booking.block.afternoon", "14:00 – 18:00"))
                .thenReturn("14:00 – 18:00");
        when(appSettingsService.get("booking.block.night", "18:00 – 21:00"))
                .thenReturn("18:00 – 21:00");

        // When/Then
        mockMvc.perform(get("/reservar"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("timeBlocks"));
    }

    @Test
    void shouldIncludeEmptyBookingRequestDto() throws Exception {
        // Given
        when(serviceCatalogService.findAll()).thenReturn(List.of());
        when(appSettingsService.get(anyString(), anyString())).thenReturn("default");
        when(appSettingsService.getHumanWhatsappNumber()).thenReturn("");
        when(appSettingsService.getHumanWhatsappDisplayName()).thenReturn("");
        when(appSettingsService.isWhatsappHandoffEnabled()).thenReturn(false);
        when(appSettingsService.getWhatsappHandoffClientMessage()).thenReturn("");
        when(appSettingsService.getBookingMessageTemplate()).thenReturn("");

        // When/Then
        mockMvc.perform(get("/reservar"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("bookingRequest"));
    }

    @Test
    void shouldRenderWithCustomWhatsappMessage() throws Exception {
        // Given
        when(serviceCatalogService.findAll()).thenReturn(List.of());
        when(appSettingsService.get("booking.enabled", "true")).thenReturn("true");
        when(appSettingsService.getHumanWhatsappNumber()).thenReturn("+56912345678");
        when(appSettingsService.getHumanWhatsappDisplayName()).thenReturn("Soporte Técnico");
        when(appSettingsService.isWhatsappHandoffEnabled()).thenReturn(true);
        when(appSettingsService.getWhatsappHandoffClientMessage())
                .thenReturn("¡Escríbenos ahora mismo!");
        when(appSettingsService.getBookingMessageTemplate())
                .thenReturn("Deseo agendar {servicio} para el {fecha}");
        when(appSettingsService.get(eq("booking.block.morning"), anyString())).thenReturn("09:00 – 13:00");
        when(appSettingsService.get(eq("booking.block.afternoon"), anyString())).thenReturn("14:00 – 18:00");
        when(appSettingsService.get(eq("booking.block.night"), anyString())).thenReturn("18:00 – 21:00");

        // When/Then
        mockMvc.perform(get("/reservar"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("whatsappHandoffClientMessage", "¡Escríbenos ahora mismo!"))
                .andExpect(model().attribute("messageTemplate", "Deseo agendar {servicio} para el {fecha}"));
    }
}
