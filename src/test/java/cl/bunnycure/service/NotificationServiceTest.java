package cl.bunnycure.service;

import cl.bunnycure.domain.enums.NotificationPreference;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.ServiceCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private WhatsAppService whatsAppService;

    @Mock
    private WhatsAppAdminAlertOutboxService whatsAppAdminAlertOutboxService;

    @Mock
    private AppSettingsService appSettingsService;

    @Mock
    private WebPushNotificationService webPushNotificationService;

    @Mock
    private NotificationLogService notificationLogService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                mailSender,
                templateEngine,
                whatsAppService,
                whatsAppAdminAlertOutboxService,
                appSettingsService,
                webPushNotificationService,
                notificationLogService
        );
    }

    @Test
    void sendAppointmentReviewRequest_DispatchesWhatsAppWhenAllowed() {
        // Arrange
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Valentina Pérez");
        customer.setPhone("+56912345678");
        customer.setNotificationPreference(NotificationPreference.WHATSAPP_ONLY);

        ServiceCatalog service = new ServiceCatalog();
        service.setId(1L);
        service.setName("Esmaltado Permanente");
        service.setDurationMinutes(60);

        Appointment appointment = Appointment.builder()
                .id(10L)
                .customer(customer)
                .service(service)
                .appointmentDate(LocalDate.now())
                .appointmentTime(LocalTime.of(15, 0))
                .build();

        // Act
        notificationService.sendAppointmentReviewRequest(appointment);

        // Assert
        verify(whatsAppService).sendValoracionServicioGoogleTemplate(appointment);
    }

    @Test
    void sendAppointmentReviewRequest_DispatchesWhatsAppWhenBothAllowed() {
        // Arrange
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Valentina Pérez");
        customer.setPhone("+56912345678");
        customer.setNotificationPreference(NotificationPreference.BOTH);

        Appointment appointment = Appointment.builder()
                .id(10L)
                .customer(customer)
                .build();

        // Act
        notificationService.sendAppointmentReviewRequest(appointment);

        // Assert
        verify(whatsAppService).sendValoracionServicioGoogleTemplate(appointment);
    }

    @Test
    void sendAppointmentReviewRequest_SkipsWhenWhatsAppNotAllowed() {
        // Arrange
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Valentina Pérez");
        customer.setPhone("+56912345678");
        customer.setNotificationPreference(NotificationPreference.EMAIL_ONLY);

        Appointment appointment = Appointment.builder()
                .id(10L)
                .customer(customer)
                .build();

        // Act
        notificationService.sendAppointmentReviewRequest(appointment);

        // Assert
        verify(whatsAppService, never()).sendValoracionServicioGoogleTemplate(any());
    }

    @Test
    void sendAppointmentReviewRequest_SkipsWhenPhoneIsMissing() {
        // Arrange
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFullName("Valentina Pérez");
        customer.setPhone(null);
        customer.setNotificationPreference(NotificationPreference.WHATSAPP_ONLY);

        Appointment appointment = Appointment.builder()
                .id(10L)
                .customer(customer)
                .build();

        // Act
        notificationService.sendAppointmentReviewRequest(appointment);

        // Assert
        verify(whatsAppService, never()).sendValoracionServicioGoogleTemplate(any());
    }
}
