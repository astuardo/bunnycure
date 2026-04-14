package cl.bunnycure.service;

import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.NotificationLog;
import cl.bunnycure.domain.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationLogService {

    private final NotificationLogRepository repository;

    @Async
    @Transactional
    public void logEmail(Appointment appointment, String recipient, String subject, String content) {
        saveLog(appointment, appointment != null ? appointment.getCustomer() : null, "EMAIL", recipient, subject, content, null);
    }

    @Async
    @Transactional
    public void logWhatsApp(Appointment appointment, String recipient, String templateName, String content, String wamid) {
        saveLog(appointment, appointment != null ? appointment.getCustomer() : null, "WHATSAPP", recipient, templateName, content, wamid);
    }

    private void saveLog(Appointment appointment, Customer customer, String channel, String recipient, String subject, String content, String wamid) {
        try {
            NotificationLog logEntry = NotificationLog.builder()
                    .appointment(appointment)
                    .customer(customer)
                    .channel(channel)
                    .recipient(recipient)
                    .subject(subject)
                    .content(content)
                    .wamid(wamid)
                    .build();
            repository.save(logEntry);
            log.debug("[NOTIFICATION-LOG] Guardado log para {}/{}", channel, recipient);
        } catch (Exception e) {
            log.error("[NOTIFICATION-LOG-ERROR] No se pudo guardar log: {}", e.getMessage());
        }
    }
}
