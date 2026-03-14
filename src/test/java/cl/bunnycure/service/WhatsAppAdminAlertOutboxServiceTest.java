package cl.bunnycure.service;

import cl.bunnycure.domain.enums.OutboxStatus;
import cl.bunnycure.domain.model.BookingRequest;
import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.domain.model.WhatsAppAdminAlertOutbox;
import cl.bunnycure.domain.repository.BookingRequestRepository;
import cl.bunnycure.domain.repository.WhatsAppAdminAlertOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppAdminAlertOutboxServiceTest {

    @Mock
    private WhatsAppAdminAlertOutboxRepository outboxRepository;

    @Mock
    private BookingRequestRepository bookingRequestRepository;

    @Mock
    private WhatsAppService whatsAppService;

    private WhatsAppAdminAlertOutboxService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppAdminAlertOutboxService(outboxRepository, bookingRequestRepository, whatsAppService);
        ReflectionTestUtils.setField(service, "adminAlertEnabled", true);
        ReflectionTestUtils.setField(service, "adminAlertNumber", "56964499995");
        ReflectionTestUtils.setField(service, "outboxEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 20);
        ReflectionTestUtils.setField(service, "maxAttempts", 6);
        ReflectionTestUtils.setField(service, "retryBaseSeconds", 30);
    }

    @Test
    void enqueueAndTryDispatch_shouldMarkEventAsSent() {
        Long bookingId = 300L;

        WhatsAppAdminAlertOutbox pending = WhatsAppAdminAlertOutbox.builder()
                .id(10L)
                .bookingRequestId(bookingId)
                .status(OutboxStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(LocalDateTime.now())
                .build();

        BookingRequest request = buildBookingRequest(bookingId);

        when(outboxRepository.findByBookingRequestId(bookingId)).thenReturn(Optional.empty());
        when(outboxRepository.save(any(WhatsAppAdminAlertOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRepository.findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(anyCollection(), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(pending));
        when(bookingRequestRepository.findById(bookingId)).thenReturn(Optional.of(request));
        when(whatsAppService.sendTextMessageSync(any(), any())).thenReturn(true);

        service.enqueueAndTryDispatch(bookingId);

        ArgumentCaptor<WhatsAppAdminAlertOutbox> captor = ArgumentCaptor.forClass(WhatsAppAdminAlertOutbox.class);
        verify(outboxRepository, atLeast(2)).save(captor.capture());
        WhatsAppAdminAlertOutbox lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(OutboxStatus.SENT, lastSaved.getStatus());
    }

    @Test
    void dispatchDueAlerts_shouldScheduleRetryOnFailure() {
        Long bookingId = 301L;

        WhatsAppAdminAlertOutbox pending = WhatsAppAdminAlertOutbox.builder()
                .id(11L)
                .bookingRequestId(bookingId)
                .status(OutboxStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(LocalDateTime.now())
                .build();

        BookingRequest request = buildBookingRequest(bookingId);

        when(outboxRepository.findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(anyCollection(), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(pending));
        when(bookingRequestRepository.findById(bookingId)).thenReturn(Optional.of(request));
        when(whatsAppService.sendTextMessageSync(any(), any())).thenReturn(false);
        when(outboxRepository.save(any(WhatsAppAdminAlertOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.dispatchDueAlerts();

        ArgumentCaptor<WhatsAppAdminAlertOutbox> captor = ArgumentCaptor.forClass(WhatsAppAdminAlertOutbox.class);
        verify(outboxRepository).save(captor.capture());
        WhatsAppAdminAlertOutbox saved = captor.getValue();

        assertEquals(OutboxStatus.RETRY, saved.getStatus());
        assertEquals(1, saved.getAttemptCount());
        assertTrue(saved.getNextAttemptAt().isAfter(LocalDateTime.now().minusSeconds(1)));
    }

    @Test
    void enqueueAndTryDispatch_shouldSkipWhenAlertDisabled() {
        ReflectionTestUtils.setField(service, "adminAlertEnabled", false);

        service.enqueueAndTryDispatch(999L);

        verify(outboxRepository, never()).save(any(WhatsAppAdminAlertOutbox.class));
        verify(whatsAppService, never()).sendTextMessageSync(any(), any());
    }

    private BookingRequest buildBookingRequest(Long bookingId) {
        ServiceCatalog catalog = ServiceCatalog.builder()
                .id(1L)
                .name("Limpieza facial")
                .build();

        return BookingRequest.builder()
                .id(bookingId)
                .fullName("Camila Perez")
                .phone("+56912345678")
                .email("camila@example.com")
                .service(catalog)
                .preferredDate(LocalDate.of(2026, 4, 10))
                .preferredBlock("Tarde")
                .notes("Sin observaciones")
                .build();
    }
}
