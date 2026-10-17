package cl.bunnycure.service;

import cl.bunnycure.domain.repository.MarketingTemplateRepository;
import cl.bunnycure.service.marketing.MarketingTemplateAiService;
import cl.bunnycure.web.dto.WhatsAppWebhookDto;
import lombok.extern.slf4j.Slf4j;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Comandos de administración por WhatsApp: la administradora pide una plantilla de marketing
 * en lenguaje natural (la genera la IA y se registra en Meta) y recibe el aviso cuando Meta la aprueba.
 * Extraído de WhatsAppWebhookService. Las dependencias inyectadas después del constructor se leen con suppliers.
 */
@Slf4j
class WhatsAppAdminTemplateCommands {

    private final WhatsAppService whatsAppService;
    private final Supplier<MarketingTemplateAiService> aiServiceSupplier;
    private final Supplier<MarketingTemplateRepository> templateRepositorySupplier;
    private final Supplier<String> frontendBaseUrlSupplier;
    private final Supplier<String> adminNumberSupplier;

    WhatsAppAdminTemplateCommands(WhatsAppService whatsAppService, Supplier<MarketingTemplateAiService> aiServiceSupplier, Supplier<MarketingTemplateRepository> templateRepositorySupplier, Supplier<String> frontendBaseUrlSupplier, Supplier<String> adminNumberSupplier) {
        this.whatsAppService = whatsAppService;
        this.aiServiceSupplier = aiServiceSupplier;
        this.templateRepositorySupplier = templateRepositorySupplier;
        this.frontendBaseUrlSupplier = frontendBaseUrlSupplier;
        this.adminNumberSupplier = adminNumberSupplier;
    }

    boolean isMarketingTemplateAiCommand(String text) {
        String lower = text.toLowerCase(Locale.ROOT).trim();
        return lower.startsWith("crear plantilla")
                || lower.startsWith("nueva plantilla")
                || lower.startsWith("crea plantilla")
                || lower.startsWith("plantilla:")
                || lower.startsWith("promo:")
                || lower.startsWith("crea una promo")
                || lower.startsWith("crear promo")
                || lower.startsWith("genera plantilla")
                || lower.startsWith("generar plantilla");
    }

    void handleAdminAiTemplateCreation(WhatsAppWebhookDto.Message message, String text) {
        MarketingTemplateAiService marketingTemplateAiService = aiServiceSupplier.get();
        String frontendBaseUrl = frontendBaseUrlSupplier.get();
        if (marketingTemplateAiService == null) {
            log.warn("[WEBHOOK-AI] ⚠️ Solicitud de plantilla recibida pero marketingTemplateAiService no está disponible");
            whatsAppService.sendTextMessage(message.getFrom(), "⚠️ Servicio de IA para plantillas no disponible en este momento.");
            return;
        }

        try {
            log.info("[WEBHOOK-AI] 🤖 Solicitud de plantilla recibida desde WhatsApp admin (from={}): {}", message.getFrom(), text);

            String prompt = cleanAiCommandPrompt(text);
            whatsAppService.sendTextMessage(
                    message.getFrom(),
                    "🤖 *Agente BunnyCure IA:* Procesando tu instrucción para la plantilla de marketing:\n"
                            + "«" + prompt + "»\n\n"
                            + "Validando políticas de Meta y registrando en caliente, dame un momento por favor 💅✨"
            );

            cl.bunnycure.web.dto.marketing.MarketingTemplateDto result =
                    marketingTemplateAiService.generateAndSaveTemplate(prompt, true, "WHATSAPP_ADMIN");

            StringBuilder reply = new StringBuilder();
            reply.append("✨ *¡Plantilla Creada y Registrada con Éxito!*\n\n");
            reply.append("📌 *Nombre Meta:* `").append(result.getName()).append("`\n");
            reply.append("🏷️ *Campaña:* ").append(result.getDisplayName()).append("\n");
            if (result.getHeaderText() != null && !result.getHeaderText().isBlank()) {
                reply.append("📢 *Cabecera:* ").append(result.getHeaderText()).append("\n\n");
            }
            reply.append("💬 *Cuerpo:* \n\"").append(result.getBodyText()).append("\"\n\n");
            if (result.getButtonText() != null && !result.getButtonText().isBlank()) {
                reply.append("🔗 *Botón:* ").append(result.getButtonText()).append(" (").append(result.getButtonUrl()).append(")\n\n");
            }

            if (result.getMetaId() != null && !result.getMetaId().isBlank()) {
                reply.append("🚀 *Estado en Meta:* Enviada a revisión automática (ID: `").append(result.getMetaId()).append("`).\n");
                reply.append("Te avisaré automáticamente por acá en cuanto Meta la apruebe.");
            } else {
                reply.append("💾 Guardada en el catálogo interno de BunnyCure.");
            }

            reply.append("\n\n📱 Ver en panel web: ")
                    .append(frontendBaseUrl != null ? frontendBaseUrl : "https://bunnycure-frontend.vercel.app")
                    .append("/marketing");

            whatsAppService.sendTextMessage(message.getFrom(), reply.toString());
        } catch (Exception ex) {
            log.error("[WEBHOOK-AI] ❌ Error creando plantilla desde WhatsApp: {}", ex.getMessage(), ex);
            whatsAppService.sendTextMessage(message.getFrom(), "❌ *Agente BunnyCure IA:* Ocurrió un error al generar la plantilla: " + ex.getMessage());
        }
    }

    private String cleanAiCommandPrompt(String text) {
        return text.replaceFirst("(?i)^(crear plantilla|nueva plantilla|crea plantilla|plantilla:|promo:|crea una promo|crear promo|genera plantilla|generar plantilla)\\s*[:,-]?\\s*", "").trim();
    }

    void handleTemplateStatusUpdate(WhatsAppWebhookDto.Value value) {
        MarketingTemplateRepository marketingTemplateRepository = templateRepositorySupplier.get();
        String frontendBaseUrl = frontendBaseUrlSupplier.get();
        if (marketingTemplateRepository == null || value == null || value.getExtraFields() == null) {
            return;
        }

        Map<String, Object> extra = value.getExtraFields();
        String templateName = null;
        if (extra.containsKey("message_template_name")) {
            templateName = String.valueOf(extra.get("message_template_name"));
        } else if (extra.containsKey("template_name")) {
            templateName = String.valueOf(extra.get("template_name"));
        } else if (extra.containsKey("element_name")) {
            templateName = String.valueOf(extra.get("element_name"));
        }

        String event = extra.containsKey("event") ? String.valueOf(extra.get("event")) : null;
        if (event == null && extra.containsKey("status")) {
            event = String.valueOf(extra.get("status"));
        }

        if (templateName != null && event != null) {
            log.info("[WEBHOOK-TEMPLATE] 🔄 Meta notificó actualización de plantilla: name='{}', event='{}'", templateName, event);
            Optional<cl.bunnycure.domain.model.MarketingTemplateEntity> opt = marketingTemplateRepository.findByNameIgnoreCase(templateName);
            if (opt.isPresent()) {
                cl.bunnycure.domain.model.MarketingTemplateEntity entity = opt.get();
                entity.setMetaStatus(event.toUpperCase(Locale.ROOT));
                marketingTemplateRepository.save(entity);
                log.info("[WEBHOOK-TEMPLATE] ✅ Estado de plantilla '{}' actualizado a '{}' en BD", entity.getName(), entity.getMetaStatus());

                if ("APPROVED".equalsIgnoreCase(event)) {
                    String adminPhone = adminNumberSupplier.get();
                    if (adminPhone != null && !adminPhone.isBlank()) {
                        String msg = "🎉 *¡Meta aprobó tu plantilla de marketing!*\n\n"
                                + "La plantilla *\"" + entity.getDisplayName() + "\"* (`" + entity.getName() + "`) "
                                + "ha sido aprobada oficialmente por WhatsApp.\n\n"
                                + "Ya está disponible para despacho en tu panel: "
                                + (frontendBaseUrl != null ? frontendBaseUrl : "https://bunnycure-frontend.vercel.app") + "/marketing";
                        whatsAppService.sendTextMessage(adminPhone, msg);
                    }
                }
            }
        }
    }
}
