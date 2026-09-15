package cl.bunnycure.service.marketing;

import cl.bunnycure.domain.model.MarketingTemplateEntity;
import cl.bunnycure.domain.repository.MarketingTemplateRepository;
import cl.bunnycure.service.WhatsAppService;
import cl.bunnycure.web.dto.marketing.MarketingTemplateDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio autónomo de IA para creación, validación de políticas de Meta y registro
 * de plantillas de WhatsApp Marketing en BunnyCure sin intervención en el IDE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingTemplateAiService {

    private static final Pattern DISCOUNT_PATTERN = Pattern.compile("(?i)(\\d{1,2})\\s*%");

    private final MarketingTemplateRepository templateRepository;
    private final WhatsAppService whatsAppService;
    private final MarketingTemplateCatalog templateCatalog;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${bunnycure.ai.gemini.api-key:${GEMINI_API_KEY:}}")
    private String geminiApiKey;

    public record GeneratedTemplateDraft(
            String name,
            String displayName,
            String occasion,
            String emoji,
            String headerText,
            String bodyText,
            String footerText,
            String buttonText,
            String buttonUrl,
            List<String> sampleVariables
    ) {}

    /**
     * Procesa un prompt en lenguaje natural, genera el copy con guardrails de Meta,
     * guarda en base de datos y opcionalmente lo registra en Meta Graph API en caliente.
     */
    @Transactional
    public MarketingTemplateDto generateAndSaveTemplate(String prompt, boolean autoRegisterInMeta, String source) {
        log.info("[AI-MARKETING] Iniciando generación de plantilla autónoma. Source='{}', Prompt='{}'", source, prompt);

        GeneratedTemplateDraft draft = generateDraft(prompt);

        String metaStatus = "NOT_REGISTERED";
        String metaId = null;

        if (autoRegisterInMeta) {
            Map<String, Object> metaPayload = buildMetaPayload(draft);
            try {
                log.info("[AI-MARKETING] Registrando plantilla '{}' en Meta Cloud API v22.0...", draft.name());
                Optional<JsonNode> metaResponse = whatsAppService.createMessageTemplate(metaPayload);
                if (metaResponse.isPresent()) {
                    JsonNode root = metaResponse.get();
                    metaId = root.path("id").asText(null);
                    metaStatus = root.path("status").asText("PENDING");
                    log.info("[AI-MARKETING] ✅ Plantilla registrada exitosamente en Meta. id={}, status={}", metaId, metaStatus);
                } else {
                    log.warn("[AI-MARKETING] ⚠️ Meta no retornó respuesta satisfactoria al registrar template '{}'", draft.name());
                }
            } catch (Exception ex) {
                log.error("[AI-MARKETING] ❌ Error al registrar plantilla en Meta: {}", ex.getMessage(), ex);
            }
        }

        MarketingTemplateEntity entity = MarketingTemplateEntity.builder()
                .name(draft.name())
                .displayName(draft.displayName())
                .occasion(draft.occasion())
                .emoji(draft.emoji())
                .category("MARKETING")
                .language("es_CL")
                .headerText(draft.headerText())
                .bodyText(draft.bodyText())
                .footerText(draft.footerText())
                .buttonText(draft.buttonText())
                .buttonUrl(draft.buttonUrl())
                .sampleVariables(String.join(",", draft.sampleVariables()))
                .metaStatus(metaStatus)
                .metaId(metaId)
                .source(source != null ? source : "AI_AGENT")
                .build();

        MarketingTemplateEntity saved = templateRepository.save(entity);
        log.info("[AI-MARKETING] ✅ Plantilla '{}' persistida en BD con ID {}", saved.getName(), saved.getId());

        return MarketingTemplateCatalog.toDefinition(saved).toDto(saved.getMetaStatus(), saved.getMetaId());
    }

    private GeneratedTemplateDraft generateDraft(String prompt) {
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            try {
                GeneratedTemplateDraft geminiDraft = generateWithGemini(prompt);
                if (geminiDraft != null) {
                    return sanitizeDraft(geminiDraft, prompt);
                }
            } catch (Exception ex) {
                log.warn("[AI-MARKETING] Falla en llamada a Gemini API: {}. Usando Salon Copy Engine de respaldo.", ex.getMessage());
            }
        }
        return generateWithSalonCopyEngine(prompt);
    }

    private GeneratedTemplateDraft generateWithGemini(String prompt) {
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + geminiApiKey.trim();

            String systemInstructions = """
                    Eres el Agente Creativo de Marketing de BunnyCure, un estudio exclusivo de manicura rusa y cuidado de uñas en Chile.
                    Crea una plantilla de WhatsApp para Meta Cloud API basada en la petición de la clienta o administradora.
                    Reglas estrictas de Meta:
                    - name: snake_case en minúsculas, solo letras y guiones bajos (ej: promo_cyberday_bunnycure), max 64 caracteres.
                    - category: MARKETING
                    - language: es_CL
                    - bodyText: DEBE incluir {{1}} para el nombre de la clienta (ej: ¡Hola {{1}}! ...). Max 1024 caracteres.
                    - headerText: breve y atractivo, max 60 caracteres.
                    - buttonText: max 25 caracteres (ej: Reservar mi cita).
                    - buttonUrl: https://reservar.bunnycure.cl
                    
                    Devuelve ÚNICAMENTE un objeto JSON con este formato exacto:
                    {
                      "name": "promo_...",
                      "displayName": "Nombre con emoji",
                      "occasion": "Ocasión o festividad",
                      "emoji": "💅",
                      "headerText": "Texto de cabecera",
                      "bodyText": "Cuerpo del mensaje incluyendo {{1}}",
                      "footerText": "BunnyCure Studio",
                      "buttonText": "Reservar mi cita",
                      "buttonUrl": "https://reservar.bunnycure.cl",
                      "sampleVariables": ["Camila"]
                    }
                    """;

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", systemInstructions + "\n\nInstrucción del usuario:\n" + prompt)
                            ))
                    ),
                    "generationConfig", Map.of(
                            "responseMimeType", "application/json",
                            "temperature", 0.7
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String jsonText = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText("");
                JsonNode data = objectMapper.readTree(jsonText);

                return new GeneratedTemplateDraft(
                        sanitizeName(data.path("name").asText("")),
                        data.path("displayName").asText("Campaña Especial ✨"),
                        data.path("occasion").asText("Promoción Especial"),
                        data.path("emoji").asText("💅"),
                        data.path("headerText").asText("¡Especial en BunnyCure! 💅✨"),
                        data.path("bodyText").asText(""),
                        data.path("footerText").asText("BunnyCure Studio"),
                        data.path("buttonText").asText("Reservar mi cita"),
                        data.path("buttonUrl").asText("https://reservar.bunnycure.cl"),
                        List.of("Camila")
                );
            }
        } catch (Exception ex) {
            log.error("[AI-MARKETING] Error consultando Gemini: {}", ex.getMessage());
        }
        return null;
    }

    /**
     * Motor de reglas y copywriting con tono de BunnyCure en Chile (garantiza 100% de disponibilidad sin API keys).
     */
    private GeneratedTemplateDraft generateWithSalonCopyEngine(String prompt) {
        String lower = prompt.toLowerCase(Locale.ROOT);

        String discount = "un descuento exclusivo";
        Matcher discountMatcher = DISCOUNT_PATTERN.matcher(prompt);
        if (discountMatcher.find()) {
            discount = "un " + discountMatcher.group(1) + "% de descuento exclusivo";
        }

        String theme = "Promoción Especial";
        String emoji = "💅";
        String baseName = "promo_especial";

        if (lower.contains("cyber") || lower.contains("black friday")) {
            theme = "Especial Cyber";
            emoji = "🛍️";
            baseName = "promo_cyber_bunnycure";
        } else if (lower.contains("verano") || lower.contains("vacaciones") || lower.contains("playa")) {
            theme = "Verano Radiante";
            emoji = "☀️";
            baseName = "promo_verano_bunnycure";
        } else if (lower.contains("otoño") || lower.contains("invierno")) {
            theme = "Temporada Fría & Cuidado";
            emoji = "🍂";
            baseName = "promo_temporada_bunnycure";
        } else if (lower.contains("amiga") || lower.contains("2x1") || lower.contains("duo")) {
            theme = "Especial Amigas 2x1";
            emoji = "👯‍♀️";
            baseName = "promo_amigas_duo";
        } else if (lower.contains("express") || lower.contains("ultima hora") || lower.contains("cupo")) {
            theme = "Cupos Relámpago";
            emoji = "⚡";
            baseName = "promo_cupos_relampago";
        } else if (lower.contains("graduacion") || lower.contains("fiesta") || lower.contains("evento")) {
            theme = "Especial Gala & Graduación";
            emoji = "✨";
            baseName = "promo_gala_graduacion";
        } else {
            // Extraer primeras palabras significativas
            String clean = extractTopicKeywords(lower);
            if (!clean.isBlank()) {
                theme = capitalizeWords(clean);
                baseName = "promo_" + clean.replaceAll("\\s+", "_");
            }
        }

        String uniqueName = generateUniqueName(baseName);
        String headerText = "¡" + theme + " en BunnyCure! " + emoji;
        if (headerText.length() > 60) {
            headerText = theme + " en BunnyCure " + emoji;
        }

        StringBuilder body = new StringBuilder();
        body.append("¡Hola {{1}}! ").append(emoji).append("✨\n\n");
        body.append("En BunnyCure queremos consentirte y preparamos una oportunidad perfecta para lucir tus manos impecables 💅💖\n\n");

        if (lower.contains("acrilic") || lower.contains("acrílic")) {
            body.append("Aprovecha ").append(discount).append(" en postura y mantenimiento de uñas acrílicas con acabado natural y nail art de tendencia.\n\n");
        } else if (lower.contains("rusa") || lower.contains("permanente")) {
            body.append("Disfruta de ").append(discount).append(" en manicura rusa combinada con esmaltado permanente de larga duración.\n\n");
        } else {
            body.append("Pensando en ti, activamos ").append(discount).append(" en nuestros servicios más pedidos de manicura y cuidado profesional de uñas.\n\n");
        }

        body.append("⚠️ Recuerda que los cupos semanales son limitados para brindarte una atención 100% personalizada.\n\n");
        body.append("¿Te gustaría asegurar tu cita desde ya?");

        return new GeneratedTemplateDraft(
                uniqueName,
                theme + " " + emoji,
                theme,
                emoji,
                headerText,
                body.toString(),
                "BunnyCure Studio",
                "Reservar mi cita",
                "https://reservar.bunnycure.cl",
                List.of("Camila")
        );
    }

    private GeneratedTemplateDraft sanitizeDraft(GeneratedTemplateDraft raw, String originalPrompt) {
        String name = generateUniqueName(raw.name() != null && !raw.name().isBlank() ? raw.name() : "promo_ai_bunnycure");
        String displayName = raw.displayName() != null ? raw.displayName() : "Campaña Especial ✨";
        String occasion = raw.occasion() != null ? raw.occasion() : "Especial";
        String emoji = raw.emoji() != null ? raw.emoji() : "✨";
        String header = raw.headerText() != null ? raw.headerText() : "¡Especial en BunnyCure! 💅✨";
        if (header.length() > 60) header = header.substring(0, 57) + "...";

        String body = raw.bodyText();
        if (body == null || body.isBlank() || !body.contains("{{1}}")) {
            body = "¡Hola {{1}}! " + emoji + "✨\n\nTenemos una sorpresa especial para ti en BunnyCure 💅💖\n\n" + originalPrompt + "\n\n¿Aseguramos tu cita para esta semana?";
        }
        if (body.length() > 1024) {
            body = body.substring(0, 1020);
        }

        String footer = raw.footerText() != null ? raw.footerText() : "BunnyCure Studio";
        if (footer.length() > 60) footer = footer.substring(0, 60);

        String buttonText = raw.buttonText() != null ? raw.buttonText() : "Reservar mi cita";
        if (buttonText.length() > 25) buttonText = buttonText.substring(0, 25);

        String buttonUrl = raw.buttonUrl() != null && raw.buttonUrl().startsWith("http") ? raw.buttonUrl() : "https://reservar.bunnycure.cl";

        return new GeneratedTemplateDraft(
                name,
                displayName,
                occasion,
                emoji,
                header,
                body,
                footer,
                buttonText,
                buttonUrl,
                List.of("Camila")
        );
    }

    private String generateUniqueName(String baseName) {
        String clean = sanitizeName(baseName);
        if (clean.isBlank()) {
            clean = "promo_ai_bunnycure";
        }
        if (!clean.startsWith("promo_") && !clean.startsWith("bunnycure_")) {
            clean = "promo_" + clean;
        }
        if (clean.length() > 50) {
            clean = clean.substring(0, 50);
        }

        // Si ya existe en catálogo o base de datos, agregar sufijo
        if (isNameTaken(clean)) {
            String candidate = clean + "_" + (System.currentTimeMillis() % 10000);
            if (candidate.length() > 64) {
                candidate = candidate.substring(0, 64);
            }
            return candidate;
        }
        return clean;
    }

    private boolean isNameTaken(String name) {
        if (templateRepository != null && templateRepository.existsByNameIgnoreCase(name)) {
            return true;
        }
        return templateCatalog != null && templateCatalog.findByName(name).isPresent();
    }

    private String sanitizeName(String raw) {
        if (raw == null) return "";
        String normalized = Normalizer.normalize(raw.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
    }

    private String extractTopicKeywords(String text) {
        String clean = sanitizeName(text);
        String[] parts = clean.split("_");
        List<String> valid = new ArrayList<>();
        Set<String> stopWords = Set.of("crear", "plantilla", "para", "con", "de", "la", "el", "un", "una", "por", "favor", "en", "que", "y", "los", "las");
        for (String p : parts) {
            if (p.length() > 2 && !stopWords.contains(p)) {
                valid.add(p);
                if (valid.size() >= 3) break;
            }
        }
        return String.join(" ", valid);
    }

    private String capitalizeWords(String text) {
        String[] words = text.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private Map<String, Object> buildMetaPayload(GeneratedTemplateDraft draft) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", draft.name());
        payload.put("category", "MARKETING");
        payload.put("language", "es_CL");

        List<Map<String, Object>> components = new ArrayList<>();

        if (draft.headerText() != null && !draft.headerText().isBlank()) {
            components.add(Map.of(
                    "type", "HEADER",
                    "format", "TEXT",
                    "text", draft.headerText()
            ));
        }

        Map<String, Object> bodyComp = new HashMap<>();
        bodyComp.put("type", "BODY");
        bodyComp.put("text", draft.bodyText());
        bodyComp.put("example", Map.of("body_text", List.of(draft.sampleVariables())));
        components.add(bodyComp);

        if (draft.footerText() != null && !draft.footerText().isBlank()) {
            components.add(Map.of(
                    "type", "FOOTER",
                    "text", draft.footerText()
            ));
        }

        if (draft.buttonText() != null && !draft.buttonText().isBlank() && draft.buttonUrl() != null && !draft.buttonUrl().isBlank()) {
            components.add(Map.of(
                    "type", "BUTTONS",
                    "buttons", List.of(Map.of(
                            "type", "URL",
                            "text", draft.buttonText(),
                            "url", draft.buttonUrl()
                    ))
            ));
        }

        payload.put("components", components);
        return payload;
    }
}
