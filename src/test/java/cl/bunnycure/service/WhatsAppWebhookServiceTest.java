package cl.bunnycure.service;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.WebhookOperationalEventRepository;
import cl.bunnycure.domain.repository.WebhookProcessedEventRepository;
import cl.bunnycure.web.dto.WhatsAppWebhookDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private WhatsAppService whatsAppService;

    @Mock
    private WebhookProcessedEventRepository webhookProcessedEventRepository;

    @Mock
    private WebhookOperationalEventRepository webhookOperationalEventRepository;

    @Mock
    private AppSettingsService appSettingsService;

    @Mock
    private WhatsAppHandoffService whatsAppHandoffService;

    @Mock
    private CustomerServiceRecordService customerServiceRecordService;

    private WhatsAppWebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new WhatsAppWebhookService(
                appointmentRepository,
                webhookOperationalEventRepository,
                webhookProcessedEventRepository,
                whatsAppService,
                appSettingsService,
                whatsAppHandoffService,
                customerServiceRecordService
        );
    }

    @Test
    void processWebhookNotification_ImageFromOwner_DelegatesToCustomerRecordService() {
        ReflectionTestUtils.setField(webhookService, "customerRecordOwnerNumber", "56964499995");

        webhookService.processWebhookNotification(webhookWithMessage(imageMessage(
                "wamid-image-1",
                "56964499995",
                "media-1",
                "CLIENTE: +56912345678\nSERVICIO: Control"
        )));

        verify(customerServiceRecordService).registerFromIncomingImage(any());
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
        when(appSettingsService.isWhatsappHandoffEnabled()).thenReturn(false);
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

    @Test
    void processWebhookNotification_InteractiveConfirm_UpdatesAppointmentStatus() {
        Appointment appointment = createAppointment(1L, AppointmentStatus.PENDING, "+56912345678");
        when(appointmentRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(appointment));

        webhookService.processWebhookNotification(
                webhookWithMessage(interactiveButtonReplyMessage("wamid-4", "56912345678", "confirmar_asistencia:1", "Confirmar asistencia"))
        );

        verify(appointmentRepository).save(appointment);
        verify(whatsAppService).sendTextMessage("56912345678", "Perfecto! Tu cita quedó confirmada. Te esperamos en BunnyCure.");
    }

    @Test
    void processWebhookNotification_InteractiveUnknown_SendsFallbackReply() {
        when(appSettingsService.isWhatsappHandoffEnabled()).thenReturn(false);
        webhookService.processWebhookNotification(
                webhookWithMessage(interactiveButtonReplyMessage("wamid-5", "56912345678", "menu_principal", "Menu principal"))
        );

        verify(appointmentRepository, never()).save(any());
        verify(whatsAppService).sendTextMessage(
                "56912345678",
                "Gracias por tu respuesta. Si necesitas ayuda con tu cita, escribe CONFIRMAR ASISTENCIA."
        );
    }

    @Test
    void processWebhookNotification_TextMessage_WithHandoffEnabled_SendsHandoffMessage() {
        when(appSettingsService.isWhatsappHandoffEnabled()).thenReturn(true);
        when(whatsAppHandoffService.buildClientHandoffMessage())
                .thenReturn("Escribenos al canal humano +56988873031");
        when(whatsAppHandoffService.buildHumanChannelLink())
                .thenReturn("https://wa.me/56988873031");

        webhookService.processWebhookNotification(webhookWithMessage(textMessage("wamid-6", "56912345678", "Necesito ayuda")));

        verify(whatsAppService).sendTextMessage(
                "56912345678",
                "Escribenos al canal humano +56988873031\nhttps://wa.me/56988873031"
        );
    }

    @Test
    void processWebhookNotification_UnknownButton_WithHandoffEnabled_SendsHandoffMessage() {
        when(appSettingsService.isWhatsappHandoffEnabled()).thenReturn(true);
        when(whatsAppHandoffService.buildClientHandoffMessage())
                .thenReturn("Atencion humana: +56988873031");
        when(whatsAppHandoffService.buildHumanChannelLink())
                .thenReturn("https://wa.me/56988873031");

        webhookService.processWebhookNotification(webhookWithMessage(buttonMessage("wamid-7", "56912345678", "Otro", "menu_principal")));

        verify(whatsAppService).sendTextMessage(
                "56912345678",
                "Atencion humana: +56988873031\nhttps://wa.me/56988873031"
        );
    }

    @Test
    void isSignatureValid_WithBytePayload_ReturnsTrueForValidSignature() throws Exception {
        byte[] payload = "{\"test\":true}".getBytes(StandardCharsets.UTF_8);
        String appSecret = "secret123";
        String signature = "sha256=" + hmacSha256Hex(payload, appSecret);

        boolean valid = webhookService.isSignatureValid(payload, signature, appSecret);

        org.junit.jupiter.api.Assertions.assertTrue(valid);
    }

    @Test
    void isSignatureValid_WithBytePayload_ReturnsFalseForInvalidSignature() {
        byte[] payload = "{\"test\":true}".getBytes(StandardCharsets.UTF_8);

        boolean valid = webhookService.isSignatureValid(payload, "sha256=deadbeef", "secret123");

        org.junit.jupiter.api.Assertions.assertFalse(valid);
    }

    @Test
    void isSignatureValid_WithMultipleHeaderValues_ReturnsTrueWhenOneSignatureMatches() throws Exception {
        byte[] payload = "{\"test\":true}".getBytes(StandardCharsets.UTF_8);
        String validSignature = hmacSha256Hex(payload, "secret123");
        String signatureHeader = "sha256=deadbeef, sha256=" + validSignature;

        boolean valid = webhookService.isSignatureValid(payload, signatureHeader, "secret123");

        org.junit.jupiter.api.Assertions.assertTrue(valid);
    }

    @Test
    void processWebhookNotification_DuplicateMessageInDatabase_IsIgnored() {
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(webhookProcessedEventRepository)
                .save(any());

        webhookService.processWebhookNotification(webhookWithMessage(textMessage("wamid-dup", "56912345678", "Hola")));

        verify(whatsAppService, never()).sendTextMessage(any(), any());
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void processWebhookNotification_OperationalRiskEvent_PersistsTrackingAndNotifiesAdmin() {
        ReflectionTestUtils.setField(webhookService, "alertAdminOnRiskEvents", true);
        ReflectionTestUtils.setField(webhookService, "adminWhatsAppNumber", "56911111111");

        webhookService.processWebhookNotification(webhookWithOperationalField("account_alerts"));

        verify(webhookOperationalEventRepository).save(any());
        verify(whatsAppService).sendTextMessage(any(), any());
    }

    @Test
    void processWebhookNotification_OperationalPersistError_DoesNotBreakFlow() {
        doThrow(new RuntimeException("db down"))
                .when(webhookOperationalEventRepository)
                .save(any());

        webhookService.processWebhookNotification(webhookWithOperationalField("message_template_status_update"));

        verify(webhookOperationalEventRepository).save(any());
    }

    @Test
    void processWebhookNotification_TemplateStatusRejected_NotifiesAdminWhenEnabled() {
        ReflectionTestUtils.setField(webhookService, "alertAdminOnRiskEvents", true);
        ReflectionTestUtils.setField(webhookService, "adminWhatsAppNumber", "56911111111");

        webhookService.processWebhookNotification(
                webhookWithOperationalField("message_template_status_update", Map.of("event", "REJECTED"))
        );

        verify(webhookOperationalEventRepository).save(any());
        verify(whatsAppService).sendTextMessage(any(), any());
    }

    private String hmacSha256Hex(byte[] payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private WhatsAppWebhookDto webhookWithMessage(WhatsAppWebhookDto.Message message) {
        WhatsAppWebhookDto.Value value = new WhatsAppWebhookDto.Value();
        value.setMessages(List.of(message));

        return webhookWithField("messages", value);
    }

    private WhatsAppWebhookDto webhookWithOperationalField(String field) {
        return webhookWithOperationalField(field, Map.of());
    }

    private WhatsAppWebhookDto webhookWithOperationalField(String field, Map<String, Object> extraFields) {
        WhatsAppWebhookDto.Metadata metadata = new WhatsAppWebhookDto.Metadata();
        metadata.setPhoneNumberId("phone-123");
        metadata.setDisplayPhoneNumber("+56 9 1111 1111");

        WhatsAppWebhookDto.Value value = new WhatsAppWebhookDto.Value();
        value.setMessagingProduct("whatsapp");
        value.setMetadata(metadata);
        if (extraFields != null) {
            extraFields.forEach(value::setExtraField);
        }

        return webhookWithField(field, value);
    }

    private WhatsAppWebhookDto webhookWithField(String field, WhatsAppWebhookDto.Value value) {

        WhatsAppWebhookDto.Change change = new WhatsAppWebhookDto.Change();
        change.setField(field);
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

    private WhatsAppWebhookDto.Message interactiveButtonReplyMessage(String id, String from, String replyId, String title) {
        WhatsAppWebhookDto.ButtonReply reply = new WhatsAppWebhookDto.ButtonReply();
        reply.setId(replyId);
        reply.setTitle(title);

        WhatsAppWebhookDto.Interactive interactive = new WhatsAppWebhookDto.Interactive();
        interactive.setType("button_reply");
        interactive.setButtonReply(reply);

        WhatsAppWebhookDto.Message message = new WhatsAppWebhookDto.Message();
        message.setId(id);
        message.setFrom(from);
        message.setType("interactive");
        message.setInteractive(interactive);
        return message;
    }

    private WhatsAppWebhookDto.Message imageMessage(String id, String from, String mediaId, String caption) {
        WhatsAppWebhookDto.Image image = new WhatsAppWebhookDto.Image();
        image.setId(mediaId);
        image.setCaption(caption);

        WhatsAppWebhookDto.Message message = new WhatsAppWebhookDto.Message();
        message.setId(id);
        message.setFrom(from);
        message.setType("image");
        message.setImage(image);
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
