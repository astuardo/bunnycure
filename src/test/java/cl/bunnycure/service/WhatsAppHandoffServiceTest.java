package cl.bunnycure.service;

import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.BookingRequest;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.ServiceCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppHandoffServiceTest {

    @Mock
    private AppSettingsService appSettingsService;

    private WhatsAppHandoffService handoffService;

    @BeforeEach
    void setUp() {
        handoffService = new WhatsAppHandoffService(appSettingsService);
    }

    @Test
    void buildHumanChannelLink_UsesNormalizedHumanNumber() {
        when(appSettingsService.getHumanWhatsappNumber()).thenReturn("+56 9 8887 3031");

        String link = handoffService.buildHumanChannelLink();

        assertEquals("https://wa.me/56988873031", link);
    }

    @Test
    void buildClientHandoffMessage_ReplacesHumanNumberToken() {
        when(appSettingsService.getHumanWhatsappNumber()).thenReturn("56988873031");
        when(appSettingsService.getWhatsappHandoffClientMessage())
                .thenReturn("Escribenos al canal humano {numero}");

        String message = handoffService.buildClientHandoffMessage();

        assertEquals("Escribenos al canal humano +56988873031", message);
    }

    @Test
    void buildAdminToCustomerLinkFromBookingRequest_IncludesEncodedContext() {
        when(appSettingsService.getWhatsappHandoffAdminPrefill())
                .thenReturn("Hola {nombre}, te contacto por {servicio} el {fecha} en bloque {bloque}.");

        BookingRequest request = BookingRequest.builder()
                .fullName("Ana Test")
                .phone("+56 9 1234 5678")
                .service(ServiceCatalog.builder().name("Manicure").build())
                .preferredDate(LocalDate.of(2026, 3, 15))
                .preferredBlock("Tarde")
                .build();

        String link = handoffService.buildAdminToCustomerLinkFromBookingRequest(request);

        assertTrue(link.startsWith("https://wa.me/56912345678?text="));
        assertTrue(link.contains("Ana+Test"));
        assertTrue(link.contains("Manicure"));
        assertTrue(link.contains("15%2F03%2F2026"));
    }

    @Test
    void buildAdminToCustomerLinkFromAppointment_IncludesDateAndTime() {
        when(appSettingsService.getWhatsappHandoffAdminPrefill())
                .thenReturn("Hola {nombre}, cita {fecha} a las {hora}.");

        Customer customer = new Customer();
        customer.setFullName("Vale Test");
        customer.setPhone("56911112222");

        Appointment appointment = Appointment.builder()
                .customer(customer)
                .service(ServiceCatalog.builder().name("Pedicure").build())
                .appointmentDate(LocalDate.of(2026, 3, 18))
                .appointmentTime(LocalTime.of(16, 30))
                .build();

        String link = handoffService.buildAdminToCustomerLinkFromAppointment(appointment);

        assertTrue(link.startsWith("https://wa.me/56911112222?text="));
        assertTrue(link.contains("18%2F03%2F2026"));
        assertTrue(link.contains("16%3A30"));
    }
}
