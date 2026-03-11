package cl.bunnycure.service;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.web.dto.WhatsAppWebhookDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private WhatsAppService whatsAppService;

    private WhatsAppWebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new WhatsAppWebhookService(appointmentRepository, whatsAppService);
    }

    @Test
    void processWebhookNotification_ConfirmButton_UpdatesAppointmentStatus() {
        Appointment appointment = createAppointment(1L, AppointmentStatus.PENDING, "+56912345678");
        when(appointmentRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(appointment));

        webhookService.processWebhookNotification(webhookWithMessage(buttonMessage("wamid-1", "56912345678", "Confirmar asistencia", "confirmar_asistencia:1")));

        verify(appointmentRepository).save(appointment);
        verify(whatsAppService).sendTextMessage("56912345678", "Perfecto! Tu cita quedó confirmada. Te esperamos en BunnyCure.");
    }

    @Test
    void processWebhookNotification_TextMessage_SendsWelcomeReply() {
        webhookService.processWebhookNotification(webhookWithMessage(textMessage("wamid-2", "56912345678", "Hola")));

        verify(whatsAppService).sendTextMessage(
                "56912345678",
                "Hola! Gracias por escribir a BunnyCure. Si quieres confirmar una cita, responde con el boton de confirmacion del mensaje que te enviamos."
        );
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void processWebhookNotification_UnknownButton_DoesNotUpdateAppointment() {
        webhookService.processWebhookNotification(webhookWithMessage(buttonMessage("wamid-3", "56912345678", "Otro", "menu_principal")));

        verify(appointmentRepository, never()).save(any());
    }

    private WhatsAppWebhookDto webhookWithMessage(WhatsAppWebhookDto.Message message) {
        WhatsAppWebhookDto.Value value = new WhatsAppWebhookDto.Value();
        value.setMessages(List.of(message));

        WhatsAppWebhookDto.Change change = new WhatsAppWebhookDto.Change();
        change.setField("messages");
        change.setValue(value);

        WhatsAppWebhookDto.Entry entry = new WhatsAppWebhookDto.Entry();
        entry.setId("entry-1");
        entry.setChanges(List.of(change));

        WhatsAppWebhookDto webhook = new WhatsAppWebhookDto();
        webhook.setObject("whatsapp_business_account");
        webhook.setEntry(List.of(entry));
        return webhook;
    }

    private WhatsAppWebhookDto.Message textMessage(String id, String from, String body) {
        WhatsAppWebhookDto.Text text = new WhatsAppWebhookDto.Text();
        text.setBody(body);

        WhatsAppWebhookDto.Message message = new WhatsAppWebhookDto.Message();
        message.setId(id);
        message.setFrom(from);
        message.setType("text");
        message.setText(text);
        return message;
    }

    private WhatsAppWebhookDto.Message buttonMessage(String id, String from, String text, String payload) {
        WhatsAppWebhookDto.Button button = new WhatsAppWebhookDto.Button();
        button.setText(text);
        button.setPayload(payload);

        WhatsAppWebhookDto.Message message = new WhatsAppWebhookDto.Message();
        message.setId(id);
        message.setFrom(from);
        message.setType("button");
        message.setButton(button);
        return message;
    }

    private Appointment createAppointment(Long id, AppointmentStatus status, String phone) {
        Customer customer = new Customer();
        customer.setId(10L);
        customer.setFullName("Ana Test");
        customer.setPhone(phone);

        ServiceCatalog service = new ServiceCatalog();
        service.setId(20L);
        service.setName("Manicure");
        service.setDurationMinutes(60);

        Appointment appointment = Appointment.builder()
                .id(id)
                .customer(customer)
                .service(service)
                .appointmentDate(LocalDate.now().plusDays(1))
                .appointmentTime(LocalTime.of(15, 0))
                .status(status)
                .build();
        return appointment;
    }
}
