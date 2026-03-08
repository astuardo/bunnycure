package cl.bunnycure.service;

import cl.bunnycure.web.dto.WhatsAppWebhookDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Servicio para procesar las notificaciones de webhook de WhatsApp
 */
@Service
public class WhatsAppWebhookService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookService.class);

    /**
     * Procesa la notificación recibida del webhook
     */
    public void processWebhookNotification(WhatsAppWebhookDto webhook) {
        try {
            log.info("[WEBHOOK] 📥 Notificación recibida de WhatsApp");
            log.info("[WEBHOOK] Object type: {}", webhook.getObject());

            if (webhook.getEntry() == null || webhook.getEntry().isEmpty()) {
                log.warn("[WEBHOOK] ⚠️ No se encontraron entries en la notificación");
                return;
            }

            // Procesar cada entry
            for (WhatsAppWebhookDto.Entry entry : webhook.getEntry()) {
                processEntry(entry);
            }

        } catch (Exception e) {
            log.error("[WEBHOOK] ❌ Error procesando notificación: {}", e.getMessage(), e);
        }
    }

    private void processEntry(WhatsAppWebhookDto.Entry entry) {
        log.info("[WEBHOOK] 📦 Procesando entry ID: {}", entry.getId());

        if (entry.getChanges() == null || entry.getChanges().isEmpty()) {
            log.warn("[WEBHOOK] ⚠️ No se encontraron cambios en el entry");
            return;
        }

        for (WhatsAppWebhookDto.Change change : entry.getChanges()) {
            processChange(change);
        }
    }

    private void processChange(WhatsAppWebhookDto.Change change) {
        log.info("[WEBHOOK] 🔄 Procesando cambio en campo: {}", change.getField());

        WhatsAppWebhookDto.Value value = change.getValue();
        if (value == null) {
            log.warn("[WEBHOOK] ⚠️ No se encontró value en el cambio");
            return;
        }

        log.info("[WEBHOOK] 📱 Messaging product: {}", value.getMessagingProduct());
        
        if (value.getMetadata() != null) {
            log.info("[WEBHOOK] 📞 Phone Number ID: {}", value.getMetadata().getPhoneNumberId());
            log.info("[WEBHOOK] 📞 Display Phone: {}", value.getMetadata().getDisplayPhoneNumber());
        }

        // Procesar mensajes recibidos
        if (value.getMessages() != null && !value.getMessages().isEmpty()) {
            processIncomingMessages(value.getMessages(), value.getContacts());
        }

        // Procesar estados de mensajes (entregado, leído, etc.)
        if (value.getStatuses() != null && !value.getStatuses().isEmpty()) {
            processMessageStatuses(value.getStatuses());
        }
    }

    private void processIncomingMessages(
            java.util.List<WhatsAppWebhookDto.Message> messages,
            java.util.List<WhatsAppWebhookDto.Contact> contacts) {
        
        log.info("[WEBHOOK] 💬 Procesando {} mensaje(s) entrante(s)", messages.size());

        for (WhatsAppWebhookDto.Message message : messages) {
            String contactName = "Unknown";
            if (contacts != null && !contacts.isEmpty()) {
                WhatsAppWebhookDto.Contact contact = contacts.get(0);
                if (contact.getProfile() != null && contact.getProfile().getName() != null) {
                    contactName = contact.getProfile().getName();
                }
            }

            log.info("[WEBHOOK] 📨 Mensaje de: {} ({})", contactName, message.getFrom());
            log.info("[WEBHOOK] 🆔 Message ID: {}", message.getId());
            log.info("[WEBHOOK] ⏰ Timestamp: {}", message.getTimestamp());
            log.info("[WEBHOOK] 📝 Tipo: {}", message.getType());

            // Procesar según el tipo de mensaje
            switch (message.getType()) {
                case "text":
                    if (message.getText() != null) {
                        log.info("[WEBHOOK] 💬 Texto: {}", message.getText().getBody());
                        // TODO: Implementar lógica de respuesta automática o procesamiento de comandos
                    }
                    break;
                
                case "image":
                    if (message.getImage() != null) {
                        log.info("[WEBHOOK] 🖼️ Imagen recibida - ID: {}", message.getImage().getId());
                        log.info("[WEBHOOK] 📝 Caption: {}", message.getImage().getCaption());
                    }
                    break;
                
                case "video":
                    if (message.getVideo() != null) {
                        log.info("[WEBHOOK] 🎥 Video recibido - ID: {}", message.getVideo().getId());
                    }
                    break;
                
                case "audio":
                    if (message.getAudio() != null) {
                        log.info("[WEBHOOK] 🎵 Audio recibido - ID: {}", message.getAudio().getId());
                    }
                    break;
                
                case "document":
                    if (message.getDocument() != null) {
                        log.info("[WEBHOOK] 📄 Documento recibido - ID: {}", message.getDocument().getId());
                        log.info("[WEBHOOK] 📝 Filename: {}", message.getDocument().getFilename());
                    }
                    break;
                
                default:
                    log.info("[WEBHOOK] ❓ Tipo de mensaje no manejado: {}", message.getType());
            }
        }
    }

    private void processMessageStatuses(java.util.List<WhatsAppWebhookDto.Status> statuses) {
        log.info("[WEBHOOK] 📊 Procesando {} estado(s) de mensaje(s)", statuses.size());

        for (WhatsAppWebhookDto.Status status : statuses) {
            log.info("[WEBHOOK] 📬 Estado de mensaje");
            log.info("[WEBHOOK] 🆔 Message ID: {}", status.getId());
            log.info("[WEBHOOK] 📱 Recipient ID: {}", status.getRecipientId());
            log.info("[WEBHOOK] ✅ Estado: {}", status.getStatus());
            log.info("[WEBHOOK] ⏰ Timestamp: {}", status.getTimestamp());

            // Estados posibles: sent, delivered, read, failed
            switch (status.getStatus()) {
                case "sent":
                    log.info("[WEBHOOK] 📤 Mensaje enviado exitosamente");
                    break;
                case "delivered":
                    log.info("[WEBHOOK] 📥 Mensaje entregado al destinatario");
                    break;
                case "read":
                    log.info("[WEBHOOK] 👀 Mensaje leído por el destinatario");
                    break;
                case "failed":
                    log.error("[WEBHOOK] ❌ Mensaje falló al enviarse");
                    break;
                default:
                    log.info("[WEBHOOK] ℹ️ Estado: {}", status.getStatus());
            }

            // Información adicional
            if (status.getConversation() != null) {
                log.info("[WEBHOOK] 💬 Conversation ID: {}", status.getConversation().getId());
                if (status.getConversation().getOrigin() != null) {
                    log.info("[WEBHOOK] 🔄 Origin type: {}", status.getConversation().getOrigin().getType());
                }
            }

            if (status.getPricing() != null) {
                log.info("[WEBHOOK] 💰 Billable: {}", status.getPricing().isBillable());
                log.info("[WEBHOOK] 💰 Category: {}", status.getPricing().getCategory());
            }
        }
    }
}
