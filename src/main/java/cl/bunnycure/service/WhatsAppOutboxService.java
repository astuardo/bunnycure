package cl.bunnycure.service;

import cl.bunnycure.config.WhatsAppConfig;
import cl.bunnycure.domain.enums.OutboxMessageType;
import cl.bunnycure.domain.enums.OutboxStatus;
import cl.bunnycure.domain.event.WhatsAppFailedMessageEvent;
import cl.bunnycure.domain.model.WhatsAppOutboxMessage;
import cl.bunnycure.domain.model.WhatsAppSendResult;
import cl.bunnycure.domain.repository.WhatsAppOutboxMessageRepository;
import cl.bunnycure.web.dto.WhatsAppOutboxMessageDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppOutboxService {

    private static final List<OutboxStatus> PENDING_STATUSES = List.of(OutboxStatus.FAILED, OutboxStatus.PENDING, OutboxStatus.RETRY);

    private final WhatsAppOutboxMessageRepository repository;
    private final NotificationLogService notificationLogService;
    private final WhatsAppService whatsAppService;
    private final WhatsAppConfig whatsAppConfig;
    private final ObjectMapper objectMapper;

    @EventListener
    @Transactional
    public void onWhatsAppFailed(WhatsAppFailedMessageEvent event) {
        log.info("[WHATSAPP-OUTBOX] Registrando mensaje fallido para {}", event.getRecipientPhone());
        try {
            String bodyJson = null;
            if (event.getBodyParams() != null && !event.getBodyParams().isEmpty()) {
                bodyJson = objectMapper.writeValueAsString(event.getBodyParams());
            }

            WhatsAppOutboxMessage message = WhatsAppOutboxMessage.builder()
                    .appointment(event.getAppointment())
                    .customer(event.getCustomer() != null ? event.getCustomer() : (event.getAppointment() != null ? event.getAppointment().getCustomer() : null))
                    .recipientPhone(event.getRecipientPhone())
                    .messageType(event.getMessageType())
                    .templateName(event.getTemplateName())
                    .languageCode(event.getLanguageCode())
                    .headerParam(event.getHeaderParam())
                    .bodyParamsJson(bodyJson)
                    .textContent(event.getTextContent())
                    .status(OutboxStatus.FAILED)
                    .attemptCount(1)
                    .lastError(event.getErrorMessage())
                    .lastAttemptAt(LocalDateTime.now())
                    .build();

            repository.save(message);
            log.info("[WHATSAPP-OUTBOX] Mensaje guardado en cola de fallidos con ID {}", message.getId());
        } catch (Exception e) {
            log.error("[WHATSAPP-OUTBOX] Error al guardar mensaje en outbox: {}", e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Page<WhatsAppOutboxMessageDto> getMessages(Pageable pageable, boolean pendingOnly) {
        Page<WhatsAppOutboxMessage> page = pendingOnly
                ? repository.findByStatusInOrderByCreatedAtDesc(PENDING_STATUSES, pageable)
                : repository.findAllByOrderByCreatedAtDesc(pageable);
        return page.map(WhatsAppOutboxMessageDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public long getPendingCount() {
        return repository.countByStatusIn(PENDING_STATUSES);
    }

    @Transactional
    public Map<String, Object> retryMessage(Long id) {
        WhatsAppOutboxMessage msg = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mensaje de outbox no encontrado con ID " + id));

        if (msg.getStatus() == OutboxStatus.DISCARDED) {
            throw new IllegalStateException("El mensaje con ID " + id + " ha sido descartado y no se puede reintentar.");
        }

        WhatsAppSendResult sendResult;
        if (msg.getMessageType() == OutboxMessageType.TEMPLATE) {
            List<String> bodyParams = parseBodyParams(msg.getBodyParamsJson());
            String languageCode = msg.getLanguageCode() != null && !msg.getLanguageCode().isBlank()
                    ? msg.getLanguageCode()
                    : whatsAppConfig.getCitaConfirmadaLanguageCode();

            sendResult = whatsAppService.sendTemplateDirect(
                    msg.getRecipientPhone(),
                    msg.getTemplateName(),
                    languageCode,
                    msg.getHeaderParam(),
                    bodyParams
            );
        } else {
            sendResult = whatsAppService.sendTextMessageDirect(
                    msg.getRecipientPhone(),
                    msg.getTextContent()
            );
        }

        msg.setLastAttemptAt(LocalDateTime.now());
        msg.setAttemptCount(msg.getAttemptCount() + 1);

        Map<String, Object> response = new HashMap<>();
        response.put("id", id);

        if (sendResult.success()) {
            msg.setStatus(OutboxStatus.SENT);
            msg.setSentAt(LocalDateTime.now());
            msg.setLastError(null);
            repository.save(msg);

            // Guardar en log de notificaciones
            if (msg.getMessageType() == OutboxMessageType.TEMPLATE) {
                String summary = String.format("Template: %s | Header: %s | Params: %s",
                        msg.getTemplateName(), msg.getHeaderParam(), msg.getBodyParamsJson());
                notificationLogService.logWhatsApp(msg.getAppointment(), msg.getRecipientPhone(), msg.getTemplateName(), summary, sendResult.wamid());
            } else {
                notificationLogService.logWhatsApp(msg.getAppointment(), msg.getRecipientPhone(), "TEXT_MESSAGE", msg.getTextContent(), sendResult.wamid());
            }

            response.put("success", true);
            response.put("message", "Mensaje enviado exitosamente.");
            response.put("wamid", sendResult.wamid());
        } else {
            msg.setStatus(OutboxStatus.FAILED);
            msg.setLastError(sendResult.errorMessage());
            repository.save(msg);

            response.put("success", false);
            response.put("message", "Fallo al enviar mensaje: " + sendResult.errorMessage());
            response.put("error", sendResult.errorMessage());
        }

        return response;
    }

    @Transactional
    public Map<String, Object> retryAll() {
        List<WhatsAppOutboxMessage> pending = repository.findByStatusInOrderByCreatedAtAsc(PENDING_STATUSES);
        int total = pending.size();
        int succeeded = 0;
        int failed = 0;

        for (WhatsAppOutboxMessage msg : pending) {
            try {
                Map<String, Object> res = retryMessage(msg.getId());
                if (Boolean.TRUE.equals(res.get("success"))) {
                    succeeded++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                log.error("[WHATSAPP-OUTBOX] Error reintentando mensaje {}: {}", msg.getId(), e.getMessage());
                failed++;
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("total", total);
        summary.put("succeeded", succeeded);
        summary.put("failed", failed);
        return summary;
    }

    @Transactional
    public void discardMessage(Long id) {
        WhatsAppOutboxMessage msg = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mensaje no encontrado con ID " + id));
        msg.setStatus(OutboxStatus.DISCARDED);
        repository.save(msg);
        log.info("[WHATSAPP-OUTBOX] Mensaje {} descartado manualmente", id);
    }

    @Transactional
    public int discardAll() {
        int count = repository.updateStatusForStatuses(OutboxStatus.DISCARDED, PENDING_STATUSES);
        log.info("[WHATSAPP-OUTBOX] {} mensajes pendientes/fallidos descartados", count);
        return count;
    }

    private List<String> parseBodyParams(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("[WHATSAPP-OUTBOX] No se pudo deserializar bodyParamsJson: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
