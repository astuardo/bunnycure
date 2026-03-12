package cl.bunnycure.service;

import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.CustomerServiceRecord;
import cl.bunnycure.domain.repository.CustomerServiceRecordRepository;
import cl.bunnycure.web.dto.WhatsAppWebhookDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceRecordServiceTest {

    @Mock
    private CustomerServiceRecordRepository customerServiceRecordRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private WhatsAppService whatsAppService;

    @InjectMocks
    private CustomerServiceRecordService customerServiceRecordService;

    @Test
    void registerFromIncomingImage_WhenFourthRecordArrives_DeletesOldestFifo() {
        Customer customer = new Customer();
        customer.setId(10L);
        customer.setPhone("+56912345678");

        WhatsAppWebhookDto.Message message = imageMessage(
                "wamid-1",
                "56964499995",
                "media-1",
                "CLIENTE: +56912345678\nSERVICIO: Control"
        );

        when(customerServiceRecordRepository.findBySourceMessageId("wamid-1")).thenReturn(Optional.empty());
        when(customerService.findByPhone("+56912345678")).thenReturn(Optional.of(customer));
        when(whatsAppService.downloadImageByMediaId("media-1"))
                .thenReturn(Optional.of(new WhatsAppService.MediaDownloadResult(new byte[]{1, 2, 3}, "image/jpeg", "abc123")));
        when(customerServiceRecordRepository.save(any(CustomerServiceRecord.class))).thenAnswer(inv -> {
            CustomerServiceRecord saved = inv.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        CustomerServiceRecord old1 = record(1L, customer, LocalDateTime.now().minusDays(4));
        CustomerServiceRecord old2 = record(2L, customer, LocalDateTime.now().minusDays(3));
        CustomerServiceRecord old3 = record(3L, customer, LocalDateTime.now().minusDays(2));
        CustomerServiceRecord newest = record(99L, customer, LocalDateTime.now().minusDays(1));

        when(customerServiceRecordRepository.findByCustomerIdOrderByCreatedAtAsc(10L))
                .thenReturn(List.of(old1, old2, old3, newest));

        customerServiceRecordService.registerFromIncomingImage(message);

        ArgumentCaptor<CustomerServiceRecord> captor = ArgumentCaptor.forClass(CustomerServiceRecord.class);
        verify(customerServiceRecordRepository).save(captor.capture());
        assertEquals("Control", captor.getValue().getServiceDetail());
        assertEquals("56912345678", captor.getValue().getClientPhoneInPayload());
        assertEquals("image/jpeg", captor.getValue().getMimeType());
        assertEquals("abc123", captor.getValue().getMediaSha256());
        assertEquals(3, captor.getValue().getPhotoData().length);
        verify(customerServiceRecordRepository).delete(old1);
    }

    @Test
    void registerFromIncomingImage_WithInvalidCaption_DoesNotPersist() {
        WhatsAppWebhookDto.Message message = imageMessage(
                "wamid-2",
                "56964499995",
                "media-2",
                "texto sin formato"
        );

        when(customerServiceRecordRepository.findBySourceMessageId("wamid-2")).thenReturn(Optional.empty());

        Optional<CustomerServiceRecord> result = customerServiceRecordService.registerFromIncomingImage(message);

        assertTrue(result.isEmpty());
        verify(customerServiceRecordRepository, never()).save(any());
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

    private CustomerServiceRecord record(Long id, Customer customer, LocalDateTime createdAt) {
        CustomerServiceRecord record = new CustomerServiceRecord();
        record.setId(id);
        record.setCustomer(customer);
        record.setCreatedAt(createdAt);
        record.setServiceDetail("x");
        record.setSourceMessageId("msg-" + id);
        record.setWhatsappMediaId("media-" + id);
        record.setSourceFromPhone("56900000000");
        record.setClientPhoneInPayload("56912345678");
        return record;
    }
}
