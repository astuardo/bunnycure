package cl.bunnycure.service;

import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.NotificationLog;
import cl.bunnycure.domain.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cl.bunnycure.web.dto.NotificationLogDto;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationLogService {

    private final NotificationLogRepository repository;

    @Transactional(readOnly = true)
    public List<NotificationLogDto> getLogsByAppointment(Long appointmentId) {
        if (appointmentId == null) {
            return Collections.emptyList();
        }
        return repository.findByAppointmentIdOrderByCreatedAtDesc(appointmentId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationLogDto> getLogsByCustomer(Long customerId) {
        if (customerId == null) {
            return Collections.emptyList();
        }
        return repository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void updateStatusByWamid(String wamid, String rawStatus) {
        if (wamid == null || wamid.isBlank() || rawStatus == null || rawStatus.isBlank()) {
            return;
        }
        try {
            repository.findFirstByWamid(wamid).ifPresent(logEntry -> {
                String normalizedStatus = rawStatus.trim().toUpperCase(Locale.ROOT);
                logEntry.setStatus(normalizedStatus);
                repository.save(logEntry);
                log.info("[NOTIFICATION-LOG] Estado actualizado a {} para wamid: {}", normalizedStatus, wamid);
            });
        } catch (Exception e) {
            log.warn("[NOTIFICATION-LOG] No se pudo actualizar estado por wamid {}: {}", wamid, e.getMessage());
        }
    }

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

    @Async
    @Transactional
    public void logMarketingWhatsApp(Customer customer, String recipient, String templateName, String content, String wamid) {
        saveLog(null, customer, "WHATSAPP", recipient, "[MARKETING] " + templateName, content, wamid);
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
                    .status("SENT")
                    .build();
            repository.save(logEntry);
            log.debug("[NOTIFICATION-LOG] Guardado log para {}/{}", channel, recipient);
        } catch (Exception e) {
            log.error("[NOTIFICATION-LOG-ERROR] No se pudo guardar log: {}", e.getMessage());
        }
    }

    private NotificationLogDto toDto(NotificationLog logEntry) {
        String customerName = null;
        if (logEntry.getCustomer() != null) {
            customerName = logEntry.getCustomer().getFullName();
        } else if (logEntry.getAppointment() != null && logEntry.getAppointment().getCustomer() != null) {
            customerName = logEntry.getAppointment().getCustomer().getFullName();
        }

        return NotificationLogDto.builder()
                .id(logEntry.getId())
                .appointmentId(logEntry.getAppointment() != null ? logEntry.getAppointment().getId() : null)
                .customerId(logEntry.getCustomer() != null ? logEntry.getCustomer().getId() : null)
                .customerName(customerName)
                .channel(logEntry.getChannel())
                .recipient(logEntry.getRecipient())
                .subject(logEntry.getSubject())
                .content(logEntry.getContent())
                .wamid(logEntry.getWamid())
                .status(logEntry.getStatus() != null ? logEntry.getStatus() : "SENT")
                .createdAt(logEntry.getCreatedAt())
                .build();
    }
}

