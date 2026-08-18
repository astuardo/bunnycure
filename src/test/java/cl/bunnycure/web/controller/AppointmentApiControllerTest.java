package cl.bunnycure.web.controller;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.exception.GlobalExceptionHandler;
import cl.bunnycure.exception.ResourceNotFoundException;
import cl.bunnycure.service.AppointmentService;
import cl.bunnycure.service.NotificationService;
import cl.bunnycure.service.SimpleApiService;
import cl.bunnycure.service.WhatsAppHandoffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AppointmentApiControllerTest {

    @Mock
    private AppointmentService appointmentService;

    @Mock
    private WhatsAppHandoffService whatsAppHandoffService;

    @Mock
    private SimpleApiService simpleApiService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AppointmentApiController appointmentApiController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(appointmentApiController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void sendWhatsAppReview_Success() throws Exception {
        // Arrange
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Camila Soto");
        customer.setPhone("+56912345678");

        ServiceCatalog service = new ServiceCatalog();
        service.setId(1L);
        service.setName("Kapping Gel");
        service.setDurationMinutes(90);
        service.setPrice(BigDecimal.valueOf(25000));
        service.setActive(true);

        Appointment appointment = Appointment.builder()
                .id(5L)
                .customer(customer)
                .service(service)
                .appointmentDate(LocalDate.of(2026, 3, 10))
                .appointmentTime(LocalTime.of(16, 0))
                .status(AppointmentStatus.COMPLETED)
                .build();

        when(appointmentService.findById(5L)).thenReturn(appointment);

        // Act & Assert
        mockMvc.perform(post("/api/appointments/5/whatsapp/review")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.appointmentId").value(5))
                .andExpect(jsonPath("$.data.customerName").value("Camila Soto"))
                .andExpect(jsonPath("$.data.phone").value("+56912345678"))
                .andExpect(jsonPath("$.data.template").value("valoracion_servicio_google"))
                .andExpect(jsonPath("$.data.status").value("DISPATCHED"));

        verify(notificationService).sendAppointmentReviewRequest(appointment);
    }

    @Test
    void sendWhatsAppReview_Returns404WhenAppointmentNotFound() throws Exception {
        // Arrange
        when(appointmentService.findById(999L))
                .thenThrow(new ResourceNotFoundException("Cita no encontrada con ID: 999"));

        // Act & Assert
        mockMvc.perform(post("/api/appointments/999/whatsapp/review")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("Cita no encontrada con ID: 999"));

        verify(notificationService, never()).sendAppointmentReviewRequest(any());
    }

    @Test
    void sendWhatsAppReview_Returns400WhenCustomerHasNoPhone() throws Exception {
        // Arrange
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Camila Soto");
        customer.setPhone(null);

        Appointment appointment = Appointment.builder()
                .id(5L)
                .customer(customer)
                .build();

        when(appointmentService.findById(5L)).thenReturn(appointment);

        // Act & Assert
        mockMvc.perform(post("/api/appointments/5/whatsapp/review")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("La clienta asociada a la cita no posee número de teléfono registrado"));

        verify(notificationService, never()).sendAppointmentReviewRequest(any());
    }
}
