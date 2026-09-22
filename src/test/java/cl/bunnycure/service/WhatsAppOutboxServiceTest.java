package cl.bunnycure.service;

import cl.bunnycure.config.WhatsAppConfig;
import cl.bunnycure.domain.enums.OutboxMessageType;
import cl.bunnycure.domain.enums.OutboxStatus;
import cl.bunnycure.domain.event.WhatsAppFailedMessageEvent;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.WhatsAppOutboxMessage;
import cl.bunnycure.domain.model.WhatsAppSendResult;
import cl.bunnycure.domain.repository.WhatsAppOutboxMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppOutboxServiceTest {

    @Mock
    private WhatsAppOutboxMessageRepository repository;

    @Mock
    private NotificationLogService notificationLogService;

    @Mock
    private WhatsAppService whatsAppService;

    @Mock
    private WhatsAppConfig whatsAppConfig;

    private ObjectMapper objectMapper;
    private WhatsAppOutboxService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new WhatsAppOutboxService(
                repository,
                notificationLogService,
                whatsAppService,
                whatsAppConfig,
                objectMapper
        );
    }

    @Test
    void onWhatsAppFailed_SavesNewMessageWithAttemptCount1AndFailedStatus() {
        Customer customer = new Customer();
        customer.setId(5L);
        customer.setFullName("Maria Perez");

        Appointment appointment = mock(Appointment.class);

        WhatsAppFailedMessageEvent event = WhatsAppFailedMessageEvent.builder()
                .appointment(appointment)
                .customer(customer)
                .recipientPhone("56999792546")
                .messageType(OutboxMessageType.TEMPLATE)
                .templateName("confirmacion_cita")
                .languageCode("es_CL")
                .headerParam("Maria Perez")
                .bodyParams(List.of("Manicure Rusa", "25/09/2026", "15:00"))
                .errorMessage("404 Not Found: Template does not exist in es_CL")
                .build();

        service.onWhatsAppFailed(event);

        ArgumentCaptor<WhatsAppOutboxMessage> captor = ArgumentCaptor.forClass(WhatsAppOutboxMessage.class);
        verify(repository).save(captor.capture());

        WhatsAppOutboxMessage saved = captor.getValue();
        assertThat(saved.getRecipientPhone()).isEqualTo("56999792546");
        assertThat(saved.getTemplateName()).isEqualTo("confirmacion_cita");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(saved.getAttemptCount()).isEqualTo(1);
        assertThat(saved.getLastError()).contains("Template does not exist");
        assertThat(saved.getBodyParamsJson()).contains("Manicure Rusa");
    }

    @Test
    void retryMessage_Success_MarksSentAndLogsWhatsApp() {
        WhatsAppOutboxMessage msg = WhatsAppOutboxMessage.builder()
                .id(1L)
                .recipientPhone("56999792546")
                .messageType(OutboxMessageType.TEMPLATE)
                .templateName("confirmacion_cita")
                .languageCode("es_CL")
                .headerParam("Maria Perez")
                .bodyParamsJson("[\"Manicure Rusa\",\"25/09/2026\",\"15:00\"]")
                .status(OutboxStatus.FAILED)
                .attemptCount(1)
                .build();

        when(repository.findById(1L)).thenReturn(Optional.of(msg));
        when(whatsAppService.sendTemplateDirect(eq("56999792546"), eq("confirmacion_cita"), eq("es_CL"), eq("Maria Perez"), anyList()))
                .thenReturn(WhatsAppSendResult.ok("wamid.123456"));

        Map<String, Object> result = service.retryMessage(1L);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(msg.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(msg.getSentAt()).isNotNull();
        assertThat(msg.getAttemptCount()).isEqualTo(2);
        verify(notificationLogService).logWhatsApp(isNull(), eq("56999792546"), eq("confirmacion_cita"), anyString(), eq("wamid.123456"));
        verify(repository).save(msg);
    }

    @Test
    void retryMessage_Fail_IncrementsAttemptCountAndKeepsFailed() {
        WhatsAppOutboxMessage msg = WhatsAppOutboxMessage.builder()
                .id(1L)
                .recipientPhone("56999792546")
                .messageType(OutboxMessageType.TEMPLATE)
                .templateName("confirmacion_cita")
                .languageCode("es_CL")
                .headerParam("Maria Perez")
                .bodyParamsJson("[\"Manicure Rusa\"]")
                .status(OutboxStatus.FAILED)
                .attemptCount(1)
                .build();

        when(repository.findById(1L)).thenReturn(Optional.of(msg));
        when(whatsAppService.sendTemplateDirect(anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(WhatsAppSendResult.fail("Still in review"));

        Map<String, Object> result = service.retryMessage(1L);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(msg.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(msg.getAttemptCount()).isEqualTo(2);
        assertThat(msg.getLastError()).isEqualTo("Still in review");
        verify(notificationLogService, never()).logWhatsApp(any(), any(), any(), any(), any());
        verify(repository).save(msg);
    }

    @Test
    void retryAll_ExecutesAllAndReturnsSummary() {
        WhatsAppOutboxMessage m1 = WhatsAppOutboxMessage.builder().id(1L).recipientPhone("56911111111").messageType(OutboxMessageType.TEXT).textContent("Hola 1").status(OutboxStatus.FAILED).attemptCount(1).build();
        WhatsAppOutboxMessage m2 = WhatsAppOutboxMessage.builder().id(2L).recipientPhone("56922222222").messageType(OutboxMessageType.TEXT).textContent("Hola 2").status(OutboxStatus.FAILED).attemptCount(1).build();

        when(repository.findByStatusInOrderByCreatedAtAsc(anyCollection())).thenReturn(List.of(m1, m2));
        when(repository.findById(1L)).thenReturn(Optional.of(m1));
        when(repository.findById(2L)).thenReturn(Optional.of(m2));

        when(whatsAppService.sendTextMessageDirect(eq("56911111111"), eq("Hola 1"))).thenReturn(WhatsAppSendResult.ok("wamid.1"));
        when(whatsAppService.sendTextMessageDirect(eq("56922222222"), eq("Hola 2"))).thenReturn(WhatsAppSendResult.fail("Error"));

        Map<String, Object> summary = service.retryAll();

        assertThat(summary.get("total")).isEqualTo(2);
        assertThat(summary.get("succeeded")).isEqualTo(1);
        assertThat(summary.get("failed")).isEqualTo(1);
    }

    @Test
    void discardMessage_MarksDiscarded() {
        WhatsAppOutboxMessage msg = WhatsAppOutboxMessage.builder()
                .id(1L)
                .status(OutboxStatus.FAILED)
                .build();

        when(repository.findById(1L)).thenReturn(Optional.of(msg));

        service.discardMessage(1L);

        assertThat(msg.getStatus()).isEqualTo(OutboxStatus.DISCARDED);
        verify(repository).save(msg);
    }

    @Test
    void discardAll_UpdatesAllPendingToDiscarded() {
        when(repository.updateStatusForStatuses(eq(OutboxStatus.DISCARDED), anyCollection())).thenReturn(5);

        int count = service.discardAll();

        assertThat(count).isEqualTo(5);
        verify(repository).updateStatusForStatuses(eq(OutboxStatus.DISCARDED), anyCollection());
    }
}
