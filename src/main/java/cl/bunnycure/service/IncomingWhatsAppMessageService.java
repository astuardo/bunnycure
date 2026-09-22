package cl.bunnycure.service;

import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.IncomingWhatsAppMessage;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.domain.repository.IncomingWhatsAppMessageRepository;
import cl.bunnycure.web.dto.IncomingWhatsAppMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncomingWhatsAppMessageService {

    private final IncomingWhatsAppMessageRepository messageRepository;
    private final CustomerRepository customerRepository;
    private final WebPushNotificationService webPushNotificationService;

    @Transactional
    public IncomingWhatsAppMessage saveIncomingMessage(String wamid, String fromPhone, String contactName, String content, String messageType) {
        if (wamid != null && !wamid.isBlank()) {
            Optional<IncomingWhatsAppMessage> existing = messageRepository.findByWamid(wamid);
            if (existing.isPresent()) {
                log.info("[WA-INBOX] ♻️ Mensaje ya guardado previamente, ignorando wamid={}", wamid);
                return existing.get();
            }
        }

        // Buscar clienta por teléfono usando coincidencias chilenas (+569 / 9...)
        Customer customer = findCustomerByPhone(fromPhone);
        String resolvedSenderName = customer != null ? customer.getFullName() : (contactName != null && !contactName.isBlank() ? contactName : fromPhone);

        IncomingWhatsAppMessage entity = IncomingWhatsAppMessage.builder()
                .wamid(wamid)
                .fromPhone(fromPhone)
                .senderName(resolvedSenderName)
                .content(content != null ? content : "")
                .messageType(messageType != null ? messageType : "text")
                .customer(customer)
                .isRead(false)
                .build();

        IncomingWhatsAppMessage saved = messageRepository.save(entity);
        log.info("[WA-INBOX] 📥 Mensaje de WhatsApp guardado en base de datos. id={}, sender={}, from={}",
                saved.getId(), resolvedSenderName, fromPhone);

        // Disparar Web Push a la administradora para alertar de inmediato
        try {
            String pushTitle = "💬 Mensaje de " + resolvedSenderName;
            String pushBody = content != null && content.length() > 100 ? content.substring(0, 97) + "..." : content;
            webPushNotificationService.sendAdminCustomNotification(pushTitle, pushBody, "/dashboard");
            log.info("[WA-INBOX] 📲 Notificación Web Push enviada a la administradora");
        } catch (Exception ex) {
            log.error("[WA-INBOX] ❌ Error enviando Web Push de mensaje entrante: {}", ex.getMessage());
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<IncomingWhatsAppMessageDto> getMessages(int page, int size, boolean unreadOnly) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
        Page<IncomingWhatsAppMessage> result = unreadOnly
                ? messageRepository.findByIsReadFalseOrderByCreatedAtDesc(pageable)
                : messageRepository.findAllByOrderByCreatedAtDesc(pageable);
        return result.map(IncomingWhatsAppMessageDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        return messageRepository.countByIsReadFalse();
    }

    @Transactional
    public boolean markAsRead(Long id) {
        Optional<IncomingWhatsAppMessage> opt = messageRepository.findById(id);
        if (opt.isPresent()) {
            IncomingWhatsAppMessage msg = opt.get();
            msg.setRead(true);
            messageRepository.save(msg);
            return true;
        }
        return false;
    }

    @Transactional
    public int markAllAsRead() {
        return messageRepository.markAllAsRead();
    }

    private Customer findCustomerByPhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }
        String normalized = rawPhone.replaceAll("\\D", "");
        return customerRepository.findAll().stream()
                .filter(c -> c.getPhone() != null && matchesPhone(c.getPhone(), normalized))
                .findFirst()
                .orElse(null);
    }

    private boolean matchesPhone(String p1, String p2) {
        String n1 = p1.replaceAll("\\D", "");
        String n2 = p2.replaceAll("\\D", "");
        if (n1.isEmpty() || n2.isEmpty()) {
            return false;
        }
        if (n1.equals(n2)) {
            return true;
        }
        String tail1 = (n1.startsWith("56") && n1.length() == 11) ? n1.substring(2) : n1;
        String tail2 = (n2.startsWith("56") && n2.length() == 11) ? n2.substring(2) : n2;
        if (tail1.equals(tail2)) {
            return true;
        }
        if (tail1.endsWith(tail2) || tail2.endsWith(tail1)) {
            int minLen = Math.min(tail1.length(), tail2.length());
            return minLen >= 8;
        }
        return false;
    }
}
