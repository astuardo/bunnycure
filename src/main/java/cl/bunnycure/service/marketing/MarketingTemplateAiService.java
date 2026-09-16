package cl.bunnycure.service.marketing;

import cl.bunnycure.domain.model.MarketingTemplateEntity;
import cl.bunnycure.domain.repository.MarketingTemplateRepository;
import cl.bunnycure.service.AppSettingsService;
import cl.bunnycure.service.WhatsAppService;
import cl.bunnycure.web.dto.marketing.MarketingTemplateDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired(required = false)
    private AppSettingsService appSettingsService;

    @Value("${bunnycure.ai.gemini.api-key:${GEMINI_API_KEY:}}")
    private String geminiApiKey;

    @Value("${bunnycure.ai.gemini.model:${GEMINI_MODEL:}}")
    private String configuredGeminiModel;

    private volatile String resolvedGeminiModel = null;

    public void setAppSettingsService(AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
    }

    public void setGeminiApiKey(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey;
    }

    public void setConfiguredGeminiModel(String configuredGeminiModel) {
        this.configuredGeminiModel = configuredGeminiModel;
    }

    private String getOrResolveGeminiModel(String apiKey) {
        if (configuredGeminiModel != null && !configuredGeminiModel.isBlank()) {
            return configuredGeminiModel.trim().replace("models/", "");
        }
        if (appSettingsService != null) {
            String fromDb = appSettingsService.get("bunnycure.ai.gemini.model", null);
            if (fromDb != null && !fromDb.isBlank()) {
                return fromDb.trim().replace("models/", "");
            }
        }
        if (resolvedGeminiModel != null) {
            return resolvedGeminiModel;
        }

        // Detección automática consultando la lista de modelos habilitados para la API key
        try {
            String listUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey.trim();
            ResponseEntity<String> response = restTemplate.getForEntity(listUrl, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode models = root.path("models");
                if (models.isArray()) {
                    List<String> candidates = new ArrayList<>();
                    for (JsonNode m : models) {
                        String name = m.path("name").asText("").replace("models/", "");
                        JsonNode methods = m.path("supportedGenerationMethods");
                        boolean canGenerate = false;
                        if (methods.isArray()) {
                            for (JsonNode method : methods) {
                                if ("generateContent".equalsIgnoreCase(method.asText())) {
                                    canGenerate = true;
                                    break;
                                }
                            }
                        }
                        if (canGenerate) {
                            candidates.add(name);
                        }
                    }

                    // Priorizar modelos Flash más recientes
                    Optional<String> flashModel = candidates.stream()
                            .filter(n -> n.contains("flash"))
                            .sorted((a, b) -> b.compareToIgnoreCase(a))
                            .findFirst();

                    if (flashModel.isPresent()) {
                        resolvedGeminiModel = flashModel.get();
                        log.info("[AI-MARKETING] ✅ Modelo Gemini activo detectado automáticamente: '{}'", resolvedGeminiModel);
                        return resolvedGeminiModel;
                    }

                    if (!candidates.isEmpty()) {
                        resolvedGeminiModel = candidates.get(0);
                        log.info("[AI-MARKETING] ✅ Modelo Gemini compatible seleccionado: '{}'", resolvedGeminiModel);
                        return resolvedGeminiModel;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[AI-MARKETING] No se pudo auto-descubrir modelos desde Gemini API: {}. Usando modelo por defecto.", ex.getMessage());
        }

        resolvedGeminiModel = "gemini-2.5-flash";
        return resolvedGeminiModel;
    }

    private String resolveGeminiApiKey() {
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            return geminiApiKey.trim();
        }
        if (appSettingsService != null) {
            String fromDb = appSettingsService.get("bunnycure.ai.gemini.api-key", null);
            if (fromDb != null && !fromDb.isBlank()) {
                return fromDb.trim();
            }
            fromDb = appSettingsService.get("GEMINI_API_KEY", null);
            if (fromDb != null && !fromDb.isBlank()) {
                return fromDb.trim();
            }
        }
        return null;
    }

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
        String apiKey = resolveGeminiApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                log.info("[AI-MARKETING] Conectando con Google Gemini 1.5 Flash para generar plantilla contextual...");
                GeneratedTemplateDraft geminiDraft = generateWithGemini(prompt, apiKey);
                if (geminiDraft != null) {
                    log.info("[AI-MARKETING] ✅ Plantilla '{}' generada exitosamente con Gemini IA", geminiDraft.name());
                    return sanitizeDraft(geminiDraft, prompt);
                }
            } catch (Exception ex) {
                log.warn("[AI-MARKETING] ⚠️ Falla en llamada a Gemini API: {}. Usando Salon Copy Engine de respaldo.", ex.getMessage());
            }
        } else {
            log.warn("[AI-MARKETING] ⚠️ Variable GEMINI_API_KEY no configurada en el servidor/entorno. Usando Salon Copy Engine local de respaldo.");
        }
        return generateWithSalonCopyEngine(prompt);
    }

    private GeneratedTemplateDraft generateWithGemini(String prompt, String apiKey) {
        String modelName = getOrResolveGeminiModel(apiKey);
        try {
            String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                    modelName, apiKey.trim());

            String systemInstructions = """
                    Eres el Agente Creativo de Marketing de BunnyCure, un estudio exclusivo de manicura rusa y cuidado de uñas en Chile.
                    Crea una plantilla de WhatsApp para Meta Cloud API basada en la petición de la clienta o administradora.
                    Reglas estrictas de Meta:
                    - name: snake_case en minúsculas, solo letras y guiones bajos (ej: promo_cyberday_bunnycure), max 64 caracteres.
                    - category: MARKETING
                    - language: es_CL
                    - bodyText: DEBE incluir {{1}} para el nombre de la clienta (ej: ¡Hola {{1}}! ...). Max 1024 caracteres.
                    - headerText: breve y atractivo, max 60 caracteres. IMPORTANTE: NO incluyas emojis, asteriscos, formato ni saltos de línea en headerText (regla estricta de Meta Cloud API).
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

        if (lower.contains("novia") || lower.contains("novio") || lower.contains("pareja")) {
            theme = "Día de la Novia";
            emoji = "💕";
            baseName = "promo_dia_de_la_novia";
        } else if (lower.contains("amarill") || lower.contains("flor")) {
            theme = "Flores Amarillas";
            emoji = "🌼";
            baseName = "promo_flores_amarillas";
        } else if (lower.contains("cyber") || lower.contains("black friday")) {
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
        String headerText = MarketingTemplateCatalog.sanitizeHeaderForMeta(theme + " en BunnyCure");

        StringBuilder body = new StringBuilder();
        body.append("¡Hola {{1}}! ").append(emoji).append("✨\n\n");
        body.append("En BunnyCure queremos consentirte y preparamos una oportunidad perfecta para lucir tus manos impecables 💅💖\n\n");

        if (lower.contains("novia") || lower.contains("novio") || lower.contains("pareja")) {
            body.append("¡Celebra el Día de la Novia con una manicura de ensueño! 💕✨\n\n");
            body.append("En BunnyCure queremos regalonearte: tenemos diseños románticos exclusivos, manicura rusa y esmaltado de máxima duración para que tus manos luzcan radiantes 💅💖\n\n");
            body.append("Aprovecha ").append(discount).append(" y reserva tu momento especial de desconexión y belleza.\n\n");
        } else if (lower.contains("amarill") || lower.contains("flor")) {
            body.append("¡Celebremos el día de las flores amarillas! 🌼✨\n\n");
            body.append("En BunnyCure queremos que florezcas con estilo: preparamos diseños botánicos exclusivos, esmaltados en tonos amarillos pastel y manicura rusa impecable para que tus manos luzcan radiantes 💅💛\n\n");
            body.append("Aprovecha ").append(discount).append(" y celebra esta fecha especial luciendo una manicura soñada.\n\n");
        } else if (lower.contains("acrilic") || lower.contains("acrílic")) {
            body.append("Aprovecha ").append(discount).append(" en postura y mantenimiento de uñas acrílicas con acabado natural y nail art de tendencia.\n\n");
        } else if (lower.contains("rusa") || lower.contains("permanente")) {
            body.append("Disfruta de ").append(discount).append(" en manicura rusa combinada con esmaltado permanente de larga duración.\n\n");
        } else {
            body.append("Pensando en ti y en la ocasión especial de ").append(theme).append(", activamos ").append(discount).append(" en nuestros servicios más pedidos de manicura y cuidado profesional de uñas 💅💖\n\n");
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
        String header = MarketingTemplateCatalog.sanitizeHeaderForMeta(
                raw.headerText() != null && !raw.headerText().isBlank() ? raw.headerText() : "Especial en BunnyCure"
        );

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
        Set<String> stopWords = Set.of(
                "crear", "crea", "haz", "hacer", "genera", "generar", "dame", "escribe", "quiero", "necesito",
                "plantilla", "campana", "campaña", "mensaje", "texto", "promo", "promocion", "promoción",
                "para", "con", "de", "del", "la", "el", "los", "las", "un", "una", "unos", "unas",
                "por", "favor", "en", "que", "se", "dan", "da", "dar", "y", "o", "al", "dia", "día",
                "dias", "días", "fecha", "estilo", "tiempo", "ano", "año", "mes"
        );
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
            String cleanHeader = MarketingTemplateCatalog.sanitizeHeaderForMeta(draft.headerText());
            if (cleanHeader != null && !cleanHeader.isBlank()) {
                components.add(Map.of(
                        "type", "HEADER",
                        "format", "TEXT",
                        "text", cleanHeader
                ));
            }
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
