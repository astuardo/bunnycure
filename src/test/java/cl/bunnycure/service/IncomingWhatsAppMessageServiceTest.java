package cl.bunnycure.service;

import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.IncomingWhatsAppMessage;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.domain.repository.IncomingWhatsAppMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncomingWhatsAppMessageServiceTest {

    @Mock
    private IncomingWhatsAppMessageRepository messageRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private WebPushNotificationService webPushNotificationService;

    private IncomingWhatsAppMessageService service;

    @BeforeEach
    void setUp() {
        service = new IncomingWhatsAppMessageService(
                messageRepository,
                customerRepository,
                webPushNotificationService
        );
    }

    @Test
    void saveIncomingMessage_WithCustomerMatch_SavesAndNotifies() {
        Customer customer = new Customer();
        customer.setId(10L);
        customer.setFullName("Valentina Gomez");
        customer.setPhone("+56 9 8765 4321");

        when(messageRepository.findByWamid("wamid.123")).thenReturn(Optional.empty());
        when(customerRepository.findAll()).thenReturn(List.of(customer));
        when(messageRepository.save(any(IncomingWhatsAppMessage.class))).thenAnswer(i -> {
            IncomingWhatsAppMessage m = i.getArgument(0);
            m.setId(1L);
            return m;
        });

        IncomingWhatsAppMessage saved = service.saveIncomingMessage(
                "wamid.123",
                "56987654321",
                "Vale",
                "Hola! Fui el dia 14",
                "text"
        );

        assertThat(saved).isNotNull();
        assertThat(saved.getFromPhone()).isEqualTo("56987654321");
        assertThat(saved.getSenderName()).isEqualTo("Valentina Gomez");
        assertThat(saved.getContent()).isEqualTo("Hola! Fui el dia 14");
        assertThat(saved.getCustomer()).isEqualTo(customer);

        verify(webPushNotificationService).sendAdminCustomNotification(
                contains("Valentina Gomez"),
                eq("Hola! Fui el dia 14"),
                eq("/dashboard")
        );
    }

    @Test
    void saveIncomingMessage_DuplicateWamid_ReturnsExistingWithoutReinsert() {
        IncomingWhatsAppMessage existing = new IncomingWhatsAppMessage();
        existing.setId(99L);
        existing.setWamid("wamid.existing");

        when(messageRepository.findByWamid("wamid.existing")).thenReturn(Optional.of(existing));

        IncomingWhatsAppMessage result = service.saveIncomingMessage(
                "wamid.existing",
                "56911112222",
                "Ana",
                "Hola",
                "text"
        );

        assertThat(result.getId()).isEqualTo(99L);
        verify(messageRepository, never()).save(any());
        verifyNoInteractions(webPushNotificationService);
    }

    @Test
    void markAsRead_UpdatesIsRead() {
        IncomingWhatsAppMessage msg = new IncomingWhatsAppMessage();
        msg.setId(5L);
        msg.setRead(false);

        when(messageRepository.findById(5L)).thenReturn(Optional.of(msg));
        when(messageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        boolean updated = service.markAsRead(5L);
        assertThat(updated).isTrue();
        assertThat(msg.isRead()).isTrue();
    }

    @Test
    void markByPhoneAsRead_CallsRepositoryWithCleanPhone() {
        when(messageRepository.markByPhoneAsRead("+56987654321", "56987654321")).thenReturn(2);

        int count = service.markByPhoneAsRead("+56987654321");
        assertThat(count).isEqualTo(2);
        verify(messageRepository).markByPhoneAsRead("+56987654321", "56987654321");
    }

    @Test
    void markByPhoneAsRead_EmptyPhone_ReturnsZero() {
        int count = service.markByPhoneAsRead("   ");
        assertThat(count).isEqualTo(0);
        verify(messageRepository, never()).markByPhoneAsRead(anyString(), anyString());
    }
}
