package cl.bunnycure.service;

import cl.bunnycure.config.WhatsAppConfig;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.BookingRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import cl.bunnycure.domain.enums.OutboxMessageType;
import cl.bunnycure.domain.event.WhatsAppFailedMessageEvent;
import cl.bunnycure.domain.model.WhatsAppSendResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio para enviar mensajes mediante WhatsApp Cloud API.
 * Documentación: https://developers.facebook.com/docs/whatsapp/cloud-api
 */
@Slf4j
@Service
public class WhatsAppService {

    private static final String WHATSAPP_API_URL = "https://graph.facebook.com/v22.0";

    private final WhatsAppConfig config;
    private final RestTemplate restTemplate;
    private final AppSettingsService appSettingsService;
    private final NotificationLogService notificationLogService;
    private final ObjectMapper objectMapper;
    private final LoyaltyRewardService loyaltyRewardService;
    private final WhatsAppMessageBuilderService messageBuilder;
    private final WhatsAppAdminAlertDispatcher adminAlertDispatcher;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private cl.bunnycure.domain.repository.TenantRepository tenantRepository;

    @Value("${bunnycure.whatsapp.admin-alert.enabled:true}")
    private boolean adminAlertEnabledFallback;

    @Autowired
    public WhatsAppService(
            WhatsAppConfig config,
            RestTemplate restTemplate,
            AppSettingsService appSettingsService,
            CalendarService calendarService,
            NotificationLogService notificationLogService,
            ObjectMapper objectMapper,
            WhatsAppHandoffService whatsAppHandoffService,
            LoyaltyRewardService loyaltyRewardService,
            WhatsAppMessageBuilderService messageBuilder,
            @Lazy WhatsAppAdminAlertDispatcher adminAlertDispatcher) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.appSettingsService = appSettingsService;
        this.notificationLogService = notificationLogService;
        this.objectMapper = objectMapper;
        this.loyaltyRewardService = loyaltyRewardService;
        this.messageBuilder = messageBuilder != null ? messageBuilder : new WhatsAppMessageBuilderService(appSettingsService);
        this.adminAlertDispatcher = adminAlertDispatcher != null ? adminAlertDispatcher : new WhatsAppAdminAlertDispatcher(
                this, this.messageBuilder, config, appSettingsService, calendarService, whatsAppHandoffService);
    }

    public WhatsAppService(
            WhatsAppConfig config,
            RestTemplate restTemplate,
            AppSettingsService appSettingsService,
            CalendarService calendarService,
            NotificationLogService notificationLogService,
            ObjectMapper objectMapper,
            WhatsAppHandoffService whatsAppHandoffService,
            LoyaltyRewardService loyaltyRewardService) {
        this(config, restTemplate, appSettingsService, calendarService, notificationLogService, objectMapper, whatsAppHandoffService, loyaltyRewardService, null, null);
    }

    public String resolveSalonName(Long tenantId) {
        if (tenantId != null && tenantRepository != null) {
            try {
                String name = tenantRepository.findById(tenantId)
                        .map(cl.bunnycure.domain.model.Tenant::getName)
                        .filter(n -> !n.isBlank())
                        .orElse(null);
                if (name != null) return name;
            } catch (Exception ex) {
                log.debug("[WHATSAPP] Error al obtener nombre de tenant {}: {}", tenantId, ex.getMessage());
            }
        }
        try {
            Long current = cl.bunnycure.config.TenantContext.getCurrentTenantId();
            if (current != null && tenantRepository != null) {
                String currentName = tenantRepository.findById(current)
                        .map(cl.bunnycure.domain.model.Tenant::getName)
                        .filter(n -> !n.isBlank())
                        .orElse(null);
                if (currentName != null) return currentName;
            }
        } catch (Exception ignored) {}
        return config.getBusinessName() != null && !config.getBusinessName().isBlank() ? config.getBusinessName() : "BunnyCure";
    }

    public Optional<MediaDownloadResult> downloadImageByMediaId(String mediaId) {
        if (mediaId == null || mediaId.isBlank()) {
            return Optional.empty();
        }
        if (config.getToken() == null || config.getToken().isBlank()) {
            log.warn("[WHATSAPP-SKIP] Token no configurado para descargar media");
            return Optional.empty();
        }

        try {
            HttpHeaders metadataHeaders = new HttpHeaders();
            metadataHeaders.setBearerAuth(config.getToken());
            HttpEntity<Void> metadataRequest = new HttpEntity<>(metadataHeaders);

            ResponseEntity<Map<String, Object>> metadataResponse = restTemplate.exchange(
                    String.format("%s/%s", WHATSAPP_API_URL, mediaId),
                    HttpMethod.GET,
                    metadataRequest,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (!metadataResponse.getStatusCode().is2xxSuccessful() || metadataResponse.getBody() == null) {
                return Optional.empty();
            }

            String mediaUrl = asString(metadataResponse.getBody().get("url"));
            if (mediaUrl == null || mediaUrl.isBlank()) {
                return Optional.empty();
            }

            HttpHeaders downloadHeaders = new HttpHeaders();
            downloadHeaders.setBearerAuth(config.getToken());
            HttpEntity<Void> downloadRequest = new HttpEntity<>(downloadHeaders);

            ResponseEntity<byte[]> downloadResponse = restTemplate.exchange(
                    mediaUrl,
                    HttpMethod.GET,
                    downloadRequest,
                    byte[].class
            );

            if (!downloadResponse.getStatusCode().is2xxSuccessful() || downloadResponse.getBody() == null) {
                return Optional.empty();
            }

            String mimeType = asString(metadataResponse.getBody().get("mime_type"));
            String sha256 = asString(metadataResponse.getBody().get("sha256"));
            return Optional.of(new MediaDownloadResult(downloadResponse.getBody(), mimeType, sha256));
        } catch (Exception ex) {
            log.warn("[WHATSAPP] No se pudo descargar media id={}: {}", mediaId, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Obtiene todos los templates creados en la cuenta de WhatsApp Business (WABA).
     * Endpoint: GET https://graph.facebook.com/v22.0/{businessAccountId}/message_templates
     *
     * @return JsonNode con la respuesta de Meta ("data", "paging") o Optional.empty() en caso de error.
     */
    public Optional<JsonNode> fetchMessageTemplates() {
        return fetchMessageTemplates(config.getBusinessAccountId());
    }

    /**
     * Obtiene todos los templates creados para una cuenta de WhatsApp Business específica.
     *
     * @param businessAccountId ID de la cuenta de WhatsApp Business (WABA ID)
     * @return JsonNode con la respuesta de Meta o Optional.empty() en caso de error.
     */
    public Optional<JsonNode> fetchMessageTemplates(String businessAccountId) {
        if (businessAccountId == null || businessAccountId.isBlank()) {
            log.warn("[WHATSAPP-TEMPLATES] Business Account ID no configurado");
            return Optional.empty();
        }
        if (config.getToken() == null || config.getToken().isBlank()) {
            log.warn("[WHATSAPP-SKIP] Token no configurado para consultar templates");
            return Optional.empty();
        }

        try {
            String url = String.format("%s/%s/message_templates", WHATSAPP_API_URL, businessAccountId.trim());

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(config.getToken());
            HttpEntity<Void> request = new HttpEntity<>(headers);

            log.info("[WHATSAPP-TEMPLATES] Consultando templates en Meta API: {}", url);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                int count = root.path("data").isArray() ? root.path("data").size() : 0;
                log.info("[WHATSAPP-TEMPLATES] ✅ Se obtuvieron {} templates exitosamente desde Meta", count);
                return Optional.of(root);
            } else {
                log.error("[WHATSAPP-TEMPLATES] ❌ Error al obtener templates. Status: {}, Body: {}",
                        response.getStatusCode(), response.getBody());
                return Optional.empty();
            }
        } catch (Exception ex) {
            log.error("[WHATSAPP-TEMPLATES] ❌ Excepción al consultar templates: {}", ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    /**
     * Crea un nuevo template en la cuenta de WhatsApp Business (WABA).
     * Endpoint: POST https://graph.facebook.com/v22.0/{businessAccountId}/message_templates
     */
    public Optional<JsonNode> createMessageTemplate(Map<String, Object> templatePayload) {
        return createMessageTemplate(config.getBusinessAccountId(), templatePayload);
    }

    public Optional<JsonNode> createMessageTemplate(String businessAccountId, Map<String, Object> templatePayload) {
        if (businessAccountId == null || businessAccountId.isBlank()) {
            log.warn("[WHATSAPP-TEMPLATES] Business Account ID no configurado");
            return Optional.empty();
        }
        if (config.getToken() == null || config.getToken().isBlank()) {
            log.warn("[WHATSAPP-SKIP] Token no configurado para crear template");
            return Optional.empty();
        }

        try {
            String url = String.format("%s/%s/message_templates", WHATSAPP_API_URL, businessAccountId.trim());

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(config.getToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(templatePayload, headers);

            log.info("[WHATSAPP-TEMPLATES] Creando template en Meta API: {} con nombre: {}",
                    url, templatePayload.get("name"));

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                log.info("[WHATSAPP-TEMPLATES] ✅ Template '{}' creado exitosamente en Meta. ID: {}",
                        templatePayload.get("name"), root.path("id").asText());
                return Optional.of(root);
            } else {
                log.error("[WHATSAPP-TEMPLATES] ❌ Error al crear template '{}'. Status: {}, Body: {}",
                        templatePayload.get("name"), response.getStatusCode(), response.getBody());
                return Optional.empty();
            }
        } catch (Exception ex) {
            log.error("[WHATSAPP-TEMPLATES] ❌ Excepción al crear template '{}': {}",
                    templatePayload.get("name"), ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    /**
     * Actualiza una plantilla existente en Meta WhatsApp Business API.
     * Endpoint: POST https://graph.facebook.com/v22.0/{templateId}
     */
    public Optional<JsonNode> updateMessageTemplate(String templateId, Map<String, Object> updatePayload) {
        if (templateId == null || templateId.isBlank()) {
            log.warn("[WHATSAPP-TEMPLATES] Template ID no configurado para actualizar");
            return Optional.empty();
        }
        if (config.getToken() == null || config.getToken().isBlank()) {
            log.warn("[WHATSAPP-SKIP] Token no configurado para actualizar template");
            return Optional.empty();
        }

        try {
            String url = String.format("%s/%s", WHATSAPP_API_URL, templateId.trim());

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(config.getToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(updatePayload, headers);

            log.info("[WHATSAPP-TEMPLATES] Actualizando template en Meta API: {}", url);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                log.info("[WHATSAPP-TEMPLATES] ✅ Template ID '{}' actualizado exitosamente en Meta", templateId);
                return Optional.of(root);
            } else {
                log.error("[WHATSAPP-TEMPLATES] ❌ Error al actualizar template ID '{}'. Status: {}, Body: {}",
                        templateId, response.getStatusCode(), response.getBody());
                return Optional.empty();
            }
        } catch (Exception ex) {
            log.error("[WHATSAPP-TEMPLATES] ❌ Excepción al actualizar template ID '{}': {}",
                    templateId, ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    /**
     * Elimina una plantilla en la cuenta de WhatsApp Business (WABA).
     * Endpoint: DELETE https://graph.facebook.com/v22.0/{businessAccountId}/message_templates?name={templateName}
     */
    public boolean deleteMessageTemplate(String templateName) {
        return deleteMessageTemplate(config.getBusinessAccountId(), templateName);
    }

    public boolean deleteMessageTemplate(String businessAccountId, String templateName) {
        if (businessAccountId == null || businessAccountId.isBlank()) {
            log.warn("[WHATSAPP-TEMPLATES] Business Account ID no configurado para eliminar template");
            return false;
        }
        if (config.getToken() == null || config.getToken().isBlank()) {
            log.warn("[WHATSAPP-SKIP] Token no configurado para eliminar template");
            return false;
        }
        if (templateName == null || templateName.isBlank()) {
            log.warn("[WHATSAPP-TEMPLATES] Nombre de template no especificado para eliminar");
            return false;
        }

        try {
            String url = String.format("%s/%s/message_templates?name=%s",
                    WHATSAPP_API_URL, businessAccountId.trim(), templateName.trim());

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(config.getToken());
            HttpEntity<Void> request = new HttpEntity<>(headers);

            log.info("[WHATSAPP-TEMPLATES] Eliminando template '{}' en Meta API: {}", templateName, url);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[WHATSAPP-TEMPLATES] ✅ Template '{}' eliminado exitosamente de Meta Cloud API", templateName);
                return true;
            } else {
                log.warn("[WHATSAPP-TEMPLATES] ⚠️ Meta retornó respuesta no exitosa al eliminar template '{}'. Status: {}, Body: {}",
                        templateName, response.getStatusCode(), response.getBody());
                return false;
            }
        } catch (Exception ex) {
            log.warn("[WHATSAPP-TEMPLATES] ⚠️ Error o excepción al eliminar template '{}' en Meta: {}", templateName, ex.getMessage());
            // Si el error es 404 Not Found, la plantilla ya no existía en Meta
            return ex.getMessage() != null && ex.getMessage().contains("404");
        }
    }

    /**
     * Marca un mensaje como leído (Read Receipt - Doble check azul en WhatsApp).
     * Endpoint: POST https://graph.facebook.com/v22.0/{phoneId}/messages
     */
    @Async
    public void markMessageAsRead(String messageId) {
        markMessageAsReadSync(messageId);
    }

    public boolean markMessageAsReadSync(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        if (config.getToken() == null || config.getToken().isBlank() || config.getPhoneId() == null || config.getPhoneId().isBlank()) {
            return false;
        }

        try {
            String url = String.format("%s/%s/messages", WHATSAPP_API_URL, config.getPhoneId());

            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("status", "read");
            payload.put("message_id", messageId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getToken());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            boolean success = response.getStatusCode().is2xxSuccessful();
            if (success) {
                log.debug("[WHATSAPP] ✅ Mensaje marcado como leído en Meta. WAMID: {}", messageId);
            }
            return success;
        } catch (Exception ex) {
            log.debug("[WHATSAPP] No se pudo marcar mensaje como leído {}: {}", messageId, ex.getMessage());
            return false;
        }
    }

    /**
     * Consulta el estado de salud, calidad y límites del número de WhatsApp.
     * Endpoint: GET https://graph.facebook.com/v22.0/{phoneId}?fields=id,verified_name,display_phone_number,quality_rating,messaging_limit_tier,code_verification_status
     */
    public Optional<JsonNode> fetchPhoneNumberHealth() {
        return fetchPhoneNumberHealth(config.getPhoneId());
    }

    public Optional<JsonNode> fetchPhoneNumberHealth(String phoneId) {
        if (phoneId == null || phoneId.isBlank() || config.getToken() == null || config.getToken().isBlank()) {
            return Optional.empty();
        }

        try {
            String url = String.format("%s/%s?fields=id,verified_name,display_phone_number,quality_rating,messaging_limit_tier,code_verification_status",
                    WHATSAPP_API_URL, phoneId.trim());

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(config.getToken());
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                log.info("[WHATSAPP-HEALTH] ✅ Estado del número obtenido. Calidad: {}, Límite: {}",
                        root.path("quality_rating").asText(), root.path("messaging_limit_tier").asText());
                return Optional.of(root);
            }
            return Optional.empty();
        } catch (Exception ex) {
            log.error("[WHATSAPP-HEALTH] ❌ Error al consultar salud del número: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Consulta el perfil de negocio de WhatsApp (descripción, dirección, websites, foto).
     * Endpoint: GET https://graph.facebook.com/v22.0/{phoneId}/whatsapp_business_profile?fields=about,address,description,email,profile_picture_url,websites,vertical
     */
    public Optional<JsonNode> fetchBusinessProfile() {
        return fetchBusinessProfile(config.getPhoneId());
    }

    public Optional<JsonNode> fetchBusinessProfile(String phoneId) {
        if (phoneId == null || phoneId.isBlank() || config.getToken() == null || config.getToken().isBlank()) {
            return Optional.empty();
        }

        try {
            String url = String.format("%s/%s/whatsapp_business_profile?fields=about,address,description,email,profile_picture_url,websites,vertical",
                    WHATSAPP_API_URL, phoneId.trim());

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(config.getToken());
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(objectMapper.readTree(response.getBody()));
            }
            return Optional.empty();
        } catch (Exception ex) {
            log.error("[WHATSAPP-PROFILE] ❌ Error al obtener perfil de negocio: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Actualiza el perfil de negocio de WhatsApp.
     * Endpoint: POST https://graph.facebook.com/v22.0/{phoneId}/whatsapp_business_profile
     */
    public boolean updateBusinessProfile(Map<String, Object> profileData) {
        return updateBusinessProfile(config.getPhoneId(), profileData);
    }

    public boolean updateBusinessProfile(String phoneId, Map<String, Object> profileData) {
        if (phoneId == null || phoneId.isBlank() || config.getToken() == null || config.getToken().isBlank()) {
            return false;
        }

        try {
            String url = String.format("%s/%s/whatsapp_business_profile", WHATSAPP_API_URL, phoneId.trim());

            Map<String, Object> payload = new HashMap<>(profileData);
            payload.put("messaging_product", "whatsapp");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getToken());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            boolean success = response.getStatusCode().is2xxSuccessful();
            log.info("[WHATSAPP-PROFILE] Actualización de perfil result: {}", success);
            return success;
        } catch (Exception ex) {
            log.error("[WHATSAPP-PROFILE] ❌ Error actualizando perfil de negocio: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Consulta analíticas de rendimiento de plantillas (entregas, lecturas, clicks, costos).
     * Endpoint: GET https://graph.facebook.com/v22.0/{businessAccountId}/template_analytics
     */
    public Optional<JsonNode> fetchTemplateAnalytics(Long startTimestamp, Long endTimestamp, String granularity, String metricTypes) {
        String businessAccountId = config.getBusinessAccountId();
        if (businessAccountId == null || businessAccountId.isBlank() || config.getToken() == null || config.getToken().isBlank()) {
            return Optional.empty();
        }

        try {
            long start = startTimestamp != null ? startTimestamp : (System.currentTimeMillis() / 1000L) - (30L * 24 * 3600);
            long end = endTimestamp != null ? endTimestamp : (System.currentTimeMillis() / 1000L);
            String gran = (granularity != null && !granularity.isBlank()) ? granularity : "DAILY";
            String metrics = (metricTypes != null && !metricTypes.isBlank()) ? metricTypes : "SENT,DELIVERED,READ,CLICKED";

            String url = String.format("%s/%s/template_analytics?start=%d&end=%d&granularity=%s&metric_types=%s",
                    WHATSAPP_API_URL, businessAccountId.trim(), start, end, gran, metrics);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(config.getToken());
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(objectMapper.readTree(response.getBody()));
            }
            return Optional.empty();
        } catch (Exception ex) {
            log.error("[WHATSAPP-ANALYTICS] ❌ Error al obtener analíticas de templates: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Envía un documento (PDF, etc.) vía WhatsApp.
     */
    public boolean sendDocumentMessage(String toPhoneNumber, String documentUrl, String filename, String caption) {
        if (config.getToken() == null || config.getToken().isBlank() || config.getPhoneId() == null || config.getPhoneId().isBlank()) {
            return false;
        }

        try {
            String url = String.format("%s/%s/messages", WHATSAPP_API_URL, config.getPhoneId());
            String normalizedPhone = normalizePhoneNumber(toPhoneNumber);

            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", normalizedPhone);
            payload.put("type", "document");

            Map<String, String> doc = new HashMap<>();
            doc.put("link", documentUrl);
            if (filename != null && !filename.isBlank()) {
                doc.put("filename", filename);
            }
            if (caption != null && !caption.isBlank()) {
                doc.put("caption", caption);
            }
            payload.put("document", doc);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getToken());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            log.error("[WHATSAPP-DOCUMENT] ❌ Error enviando documento a {}: {}", toPhoneNumber, ex.getMessage());
            return false;
        }
    }

    /**
     * Envía una imagen vía WhatsApp.
     */
    public boolean sendImageMessage(String toPhoneNumber, String imageUrl, String caption) {
        if (config.getToken() == null || config.getToken().isBlank() || config.getPhoneId() == null || config.getPhoneId().isBlank()) {
            return false;
        }

        try {
            String url = String.format("%s/%s/messages", WHATSAPP_API_URL, config.getPhoneId());
            String normalizedPhone = normalizePhoneNumber(toPhoneNumber);

            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", normalizedPhone);
            payload.put("type", "image");

            Map<String, String> image = new HashMap<>();
            image.put("link", imageUrl);
            if (caption != null && !caption.isBlank()) {
                image.put("caption", caption);
            }
            payload.put("image", image);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getToken());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            log.error("[WHATSAPP-IMAGE] ❌ Error enviando imagen a {}: {}", toPhoneNumber, ex.getMessage());
            return false;
        }
    }

    /**
     * Envía un mensaje interactivo con botones de respuesta rápida (hasta 3 botones).
     */
    public boolean sendInteractiveButtonMessage(String toPhoneNumber, String bodyText, List<Map<String, String>> buttons) {
        if (config.getToken() == null || config.getToken().isBlank() || config.getPhoneId() == null || config.getPhoneId().isBlank()) {
            return false;
        }

        try {
            String url = String.format("%s/%s/messages", WHATSAPP_API_URL, config.getPhoneId());
            String normalizedPhone = normalizePhoneNumber(toPhoneNumber);

            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", normalizedPhone);
            payload.put("type", "interactive");

            Map<String, Object> interactive = new HashMap<>();
            interactive.put("type", "button");

            Map<String, String> body = new HashMap<>();
            body.put("text", bodyText);
            interactive.put("body", body);

            List<Map<String, Object>> actionButtons = new ArrayList<>();
            for (Map<String, String> btn : buttons) {
                Map<String, Object> b = new HashMap<>();
                b.put("type", "reply");
                Map<String, String> reply = new HashMap<>();
                reply.put("id", btn.get("id"));
                reply.put("title", btn.get("title"));
                b.put("reply", reply);
                actionButtons.add(b);
            }

            Map<String, Object> action = new HashMap<>();
            action.put("buttons", actionButtons);
            interactive.put("action", action);

            payload.put("interactive", interactive);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getToken());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            log.error("[WHATSAPP-INTERACTIVE] ❌ Error enviando botones interactivos a {}: {}", toPhoneNumber, ex.getMessage());
            return false;
        }
    }

    /**
     * Envía un mensaje interactivo con lista desplegable de opciones (hasta 10 filas).
     */
    public boolean sendInteractiveListMessage(String toPhoneNumber, String headerText, String bodyText, String footerText, String buttonLabel, List<Map<String, Object>> sections) {
        if (config.getToken() == null || config.getToken().isBlank() || config.getPhoneId() == null || config.getPhoneId().isBlank()) {
            return false;
        }

        try {
            String url = String.format("%s/%s/messages", WHATSAPP_API_URL, config.getPhoneId());
            String normalizedPhone = normalizePhoneNumber(toPhoneNumber);

            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", normalizedPhone);
            payload.put("type", "interactive");

            Map<String, Object> interactive = new HashMap<>();
            interactive.put("type", "list");

            if (headerText != null && !headerText.isBlank()) {
                Map<String, String> header = new HashMap<>();
                header.put("type", "text");
                header.put("text", headerText);
                interactive.put("header", header);
            }

            Map<String, String> body = new HashMap<>();
            body.put("text", bodyText);
            interactive.put("body", body);

            if (footerText != null && !footerText.isBlank()) {
                Map<String, String> footer = new HashMap<>();
                footer.put("text", footerText);
                interactive.put("footer", footer);
            }

            Map<String, Object> action = new HashMap<>();
            action.put("button", (buttonLabel != null && !buttonLabel.isBlank()) ? buttonLabel : "Ver Opciones");
            action.put("sections", sections);
            interactive.put("action", action);

            payload.put("interactive", interactive);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getToken());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            log.error("[WHATSAPP-LIST] ❌ Error enviando lista interactiva a {}: {}", toPhoneNumber, ex.getMessage());
            return false;
        }
    }

    /**
     * Envía un mensaje de texto simple a un número de WhatsApp
     */
    @Async
    public void sendTextMessage(String toPhoneNumber, String message) {
        sendTextMessageSync(toPhoneNumber, message);
    }

    public boolean sendTextMessageSync(String toPhoneNumber, String message) {
        return sendTextMessageSync(toPhoneNumber, message, null);
    }

    public WhatsAppSendResult sendTextMessageDirect(String toPhoneNumber, String message) {
        try {
            if (config.getToken() == null || config.getToken().isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Token no configurado");
                return WhatsAppSendResult.fail("Token de WhatsApp no configurado");
            }

            if (config.getPhoneId() == null || config.getPhoneId().isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Phone ID no configurado");
                return WhatsAppSendResult.fail("Phone ID de WhatsApp no configurado");
            }

            String url = String.format("%s/%s/messages", WHATSAPP_API_URL, config.getPhoneId());
            String normalizedPhone = normalizePhoneNumber(toPhoneNumber);

            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", normalizedPhone);
            payload.put("type", "text");
            
            Map<String, String> text = new HashMap<>();
            text.put("body", message);
            payload.put("text", text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getToken());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            log.debug("[WHATSAPP-DEBUG] Enviando a URL: {}", url);
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            log.info("[WHATSAPP-RESPONSE] Status: {}", response.getStatusCode());
            log.info("[WHATSAPP-RESPONSE] Body: {}", response.getBody());

            String wamid = null;
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                try {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode messages = root.path("messages");
                    if (messages.isArray() && !messages.isEmpty()) {
                        wamid = messages.get(0).path("id").asText();
                    }
                } catch (Exception e) {
                    log.warn("[WHATSAPP-LOG] No se pudo parsear wamid de la respuesta: {}", e.getMessage());
                }
                return WhatsAppSendResult.ok(wamid);
            } else {
                return WhatsAppSendResult.fail("Status: " + response.getStatusCode() + " - " + response.getBody());
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String err = e.getStatusCode() + ": " + e.getResponseBodyAsString();
            log.error("[WHATSAPP] ❌ Error HTTP al enviar mensaje a {}: {}", toPhoneNumber, err);
            return WhatsAppSendResult.fail(err);
        } catch (Exception e) {
            log.error("[WHATSAPP] ❌ Excepción al enviar mensaje a {}: {}", toPhoneNumber, e.getMessage());
            return WhatsAppSendResult.fail(e.getMessage());
        }
    }

    public boolean sendTextMessageSync(String toPhoneNumber, String message, Appointment appointment) {
        String normalizedPhone = normalizePhoneNumber(toPhoneNumber);
        WhatsAppSendResult result = sendTextMessageDirect(toPhoneNumber, message);

        if (result.success()) {
            log.info("[WHATSAPP] ✅ Mensaje enviado exitosamente a {}. WAMID: {}", normalizedPhone, result.wamid());
            notificationLogService.logWhatsApp(appointment, normalizedPhone, "TEXT_MESSAGE", message, result.wamid());
            return true;
        } else {
            log.error("[WHATSAPP] ❌ Error al enviar mensaje a {}: {}", normalizedPhone, result.errorMessage());
            if (eventPublisher != null) {
                eventPublisher.publishEvent(WhatsAppFailedMessageEvent.builder()
                        .appointment(appointment)
                        .customer(appointment != null ? appointment.getCustomer() : null)
                        .recipientPhone(normalizedPhone)
                        .messageType(OutboxMessageType.TEXT)
                        .textContent(message)
                        .errorMessage(result.errorMessage())
                        .build());
            }
            return false;
        }
    }

    /**
     * Envía mensaje de confirmación de cita por WhatsApp
     */
    @Async
    public void sendAppointmentConfirmation(Appointment appointment) {
        try {
            String phone = appointment.getCustomer().getPhone();
            if (phone == null || phone.isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Cliente {} no tiene teléfono configurado",
                        appointment.getCustomer().getFullName());
                return;
            }

            if (config.isUseTemplateForConfirmation()) {
                sendCitaConfirmadaTemplate(appointment);
                return;
            }

            String salonName = resolveSalonName(appointment.getTenantId());
            String message = messageBuilder.buildAppointmentConfirmationText(appointment, salonName);
            sendTextMessage(phone, message);

        } catch (Exception e) {
            log.error("[WHATSAPP] Error al enviar confirmación de cita: {}", e.getMessage(), e);
        }
    }

    /**
     * Envía mensaje de cancelación de cita por WhatsApp
     */
    @Async
    public void sendAppointmentCancellation(Appointment appointment) {
        try {
            String phone = appointment.getCustomer().getPhone();
            if (phone == null || phone.isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Cliente {} no tiene teléfono configurado",
                        appointment.getCustomer().getFullName());
                return;
            }

            String salonName = resolveSalonName(appointment.getTenantId());
            String message = messageBuilder.buildAppointmentCancellationText(appointment, salonName);
            sendTextMessage(phone, message);

        } catch (Exception e) {
            log.error("[WHATSAPP] Error al enviar cancelación de cita: {}", e.getMessage(), e);
        }
    }

    /**
     * Envía mensaje de recordatorio de cita por WhatsApp
     */
    @Async
    public void sendAppointmentReminder(Appointment appointment) {
        try {
            String phone = appointment.getCustomer().getPhone();
            if (phone == null || phone.isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Cliente {} no tiene teléfono configurado",
                        appointment.getCustomer().getFullName());
                return;
            }

            String salonName = resolveSalonName(appointment.getTenantId());
            String message = messageBuilder.buildAppointmentReminderText(appointment, salonName);
            sendTextMessage(phone, message);

        } catch (Exception e) {
            log.error("[WHATSAPP] Error al enviar recordatorio de cita: {}", e.getMessage(), e);
        }
    }

    /**
     * Envía confirmación de recepción de solicitud de reserva
     */
    @Async
    public void sendBookingRequestReceived(BookingRequest request) {
        try {
            String phone = request.getPhone();
            if (phone == null || phone.isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Solicitud {} no tiene teléfono configurado", request.getId());
                return;
            }

            String salonName = resolveSalonName(request.getTenantId());
            String message = messageBuilder.buildBookingRequestReceivedText(request, salonName);
            sendTextMessage(phone, message);

        } catch (Exception e) {
            log.error("[WHATSAPP] Error al enviar confirmación de solicitud: {}", e.getMessage(), e);
        }
    }

    /**
     * Envía mensaje de rechazo de solicitud de reserva
     */
    @Async
    public void sendBookingRequestRejected(BookingRequest request) {
        try {
            String phone = request.getPhone();
            if (phone == null || phone.isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Solicitud {} no tiene teléfono configurado", request.getId());
                return;
            }

            String salonName = resolveSalonName(request.getTenantId());
            String message = messageBuilder.buildBookingRequestRejectedText(request, salonName);
            sendTextMessage(phone, message);

        } catch (Exception e) {
            log.error("[WHATSAPP] Error al enviar rechazo de solicitud: {}", e.getMessage(), e);
        }
    }

    /**
     * Envía un template pre-aprobado de WhatsApp
     * Los templates deben estar aprobados en Meta Business Manager
     * 
     * @param toPhoneNumber Número de destino
     * @param templateName Nombre del template (ej: "hello_world")
     * @param languageCode Código de idioma (ej: "en_US", "es_MX")
     */
    @Async
    public void sendTemplate(String toPhoneNumber, String templateName, String languageCode) {
        sendTemplateSync(toPhoneNumber, templateName, languageCode, null, List.of());
    }

    /**
     * Envía un template pre-aprobado de WhatsApp con parámetros body.
     */
    @Async
    public void sendTemplate(String toPhoneNumber, String templateName, String languageCode, List<String> bodyParams) {
        sendTemplateSync(toPhoneNumber, templateName, languageCode, null, bodyParams);
    }

    /**
     * Envía un template pre-aprobado de WhatsApp con parámetros para HEADER y BODY.
     *
     * @param headerParam parámetro único para HEADER (si el template tiene HEADER con {{1}})
     * @param bodyParams parámetros del BODY en orden posicional
     */
    @Async
    public void sendTemplate(String toPhoneNumber,
                             String templateName,
                             String languageCode,
                             String headerParam,
                             List<String> bodyParams) {
        sendTemplateSync(toPhoneNumber, templateName, languageCode, headerParam, bodyParams);
    }

    public boolean sendTemplateSync(String toPhoneNumber,
                                    String templateName,
                                    String languageCode,
                                    String headerParam,
                                    List<String> bodyParams) {
        return sendTemplateSync(toPhoneNumber, templateName, languageCode, headerParam, bodyParams, null);
    }

    public WhatsAppSendResult sendTemplateDirect(String toPhoneNumber,
                                                 String templateName,
                                                 String languageCode,
                                                 String headerParam,
                                                 List<String> bodyParams) {
        try {
            if (config.getToken() == null || config.getToken().isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Token no configurado");
                return WhatsAppSendResult.fail("Token de WhatsApp no configurado");
            }

            if (config.getPhoneId() == null || config.getPhoneId().isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Phone ID no configurado");
                return WhatsAppSendResult.fail("Phone ID de WhatsApp no configurado");
            }

            String url = String.format("%s/%s/messages", WHATSAPP_API_URL, config.getPhoneId());
            String normalizedPhone = normalizePhoneNumber(toPhoneNumber);
            String lang = languageCode != null && !languageCode.isBlank() ? languageCode : config.getCitaConfirmadaLanguageCode();

            Map<String, Object> payload = messageBuilder.buildTemplatePayload(
                    normalizedPhone,
                    templateName,
                    lang,
                    headerParam,
                    bodyParams);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getToken());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            log.debug("[WHATSAPP-TEMPLATE] Enviando template '{}' a URL: {}", templateName, url);
            log.debug("[WHATSAPP-TEMPLATE] Payload: {}", payload);
            log.debug("[WHATSAPP-TEMPLATE] Número normalizado: {}", normalizedPhone);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            log.info("[WHATSAPP-TEMPLATE] Status: {}", response.getStatusCode());
            log.info("[WHATSAPP-TEMPLATE] Body: {}", response.getBody());

            String wamid = null;
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                try {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode messages = root.path("messages");
                    if (messages.isArray() && !messages.isEmpty()) {
                        wamid = messages.get(0).path("id").asText();
                    }
                } catch (Exception e) {
                    log.warn("[WHATSAPP-LOG] No se pudo parsear wamid de la respuesta: {}", e.getMessage());
                }
                return WhatsAppSendResult.ok(wamid);
            } else {
                return WhatsAppSendResult.fail("Status: " + response.getStatusCode() + " - " + response.getBody());
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String err = e.getStatusCode() + ": " + e.getResponseBodyAsString();
            log.error("[WHATSAPP] ❌ Error HTTP al enviar template '{}' a {}: {}", templateName, toPhoneNumber, err);
            return WhatsAppSendResult.fail(err);
        } catch (Exception e) {
            log.error("[WHATSAPP] ❌ Excepción al enviar template '{}' a {}: {}", templateName, toPhoneNumber, e.getMessage());
            return WhatsAppSendResult.fail(e.getMessage());
        }
    }

    public boolean sendTemplateSync(String toPhoneNumber,
                                    String templateName,
                                    String languageCode,
                                    String headerParam,
                                    List<String> bodyParams,
                                    Appointment appointment) {
        String normalizedPhone = normalizePhoneNumber(toPhoneNumber);
        WhatsAppSendResult result = sendTemplateDirect(toPhoneNumber, templateName, languageCode, headerParam, bodyParams);

        if (result.success()) {
            log.info("[WHATSAPP] ✅ Template '{}' enviado exitosamente a {}. WAMID: {}", templateName, normalizedPhone, result.wamid());
            String contentSummary = String.format("Template: %s | Header: %s | Params: %s", 
                    templateName, headerParam, bodyParams);
            notificationLogService.logWhatsApp(appointment, normalizedPhone, templateName, contentSummary, result.wamid());
            return true;
        } else {
            log.error("[WHATSAPP] ❌ Error al enviar template '{}' a {}: {}", templateName, normalizedPhone, result.errorMessage());
            if (eventPublisher != null) {
                eventPublisher.publishEvent(WhatsAppFailedMessageEvent.builder()
                        .appointment(appointment)
                        .customer(appointment != null ? appointment.getCustomer() : null)
                        .recipientPhone(normalizedPhone)
                        .messageType(OutboxMessageType.TEMPLATE)
                        .templateName(templateName)
                        .languageCode(languageCode)
                        .headerParam(headerParam)
                        .bodyParams(bodyParams)
                        .errorMessage(result.errorMessage())
                        .build());
            }
            return false;
        }
    }

    public boolean sendAdminBookingAlertSync(String toPhoneNumber, BookingRequest request) {
        return adminAlertDispatcher.sendAdminBookingAlertSync(toPhoneNumber, request);
    }

    @Async
    public void sendCitaConfirmadaTemplate(Appointment appointment) {
        sendCitaConfirmadaTemplate(appointment, false, false);
    }

    @Async
    public void sendCitaConfirmadaTemplate(Appointment appointment, boolean dateChanged, boolean timeChanged) {
        String phone = appointment.getCustomer().getPhone();
        if (phone == null || phone.isBlank()) {
            log.warn("[WHATSAPP-SKIP] Cliente {} no tiene teléfono configurado",
                    appointment.getCustomer().getFullName());
            return;
        }

        var customer = appointment.getCustomer();
        int stamps = customer.getLoyaltyStamps() != null ? customer.getLoyaltyStamps() : 0;
        int rewardIndex = customer.getCurrentRewardIndex() != null ? customer.getCurrentRewardIndex() : 0;
        var reward = loyaltyRewardService.getRewardAt(rewardIndex);
        String rewardName = (reward != null) ? reward.getName() : "un premio especial";

        String loyaltyInfo;
        if (stamps >= 10) {
            loyaltyInfo = String.format("¡Felicidades! Esta es tu cita #11. ¡Hoy disfrutas de tu premio: %s! 🎉", rewardName);
        } else {
            loyaltyInfo = String.format("Llevas %d/10 marcas para ganarte el premio: %s 🐇", stamps, rewardName);
        }

        WhatsAppMessageBuilderService.TemplateParameters params =
                messageBuilder.buildCitaConfirmadaParams(appointment, dateChanged, timeChanged, loyaltyInfo);

        sendTemplateSync(
                phone,
                config.getCitaConfirmadaTemplateName(),
                config.getCitaConfirmadaLanguageCode(),
                params.headerParam(),
                params.bodyParams(),
                appointment
        );
    }

    /**
     * Envía el template recordatorio_cita con placeholders:
     * HEADER {{1}}=cliente, BODY {{1}}=servicio, {{2}}=fecha, {{3}}=hora
     */
    @Async
    public void sendRecordatorioCitaTemplate(Appointment appointment) {
        if (!config.isUseTemplateForReminder()) {
            log.info("[WHATSAPP] Template de recordatorio deshabilitado, enviando mensaje de texto");
            sendAppointmentReminder(appointment);
            return;
        }

        String phone = appointment.getCustomer().getPhone();
        if (phone == null || phone.isBlank()) {
            log.warn("[WHATSAPP-SKIP] Cliente {} no tiene teléfono configurado",
                    appointment.getCustomer().getFullName());
            return;
        }

        WhatsAppMessageBuilderService.TemplateParameters params =
                messageBuilder.buildRecordatorioParams(appointment);

        sendTemplateSync(
                phone,
                config.getRecordatorioCitaTemplateName(),
                config.getCitaConfirmadaLanguageCode(),
                params.headerParam(),
                params.bodyParams(),
                appointment
        );
    }

    /**
     * Envía el template cancelacion_cita con placeholders:
     * HEADER {{1}}=cliente, BODY {{1}}=servicio, {{2}}=fecha, {{3}}=hora, {{4}}=motivo
     */
    @Async
    public void sendCancelacionCitaTemplate(Appointment appointment) {
        if (!config.isUseTemplateForCancellation()) {
            log.info("[WHATSAPP-SKIP] Template de cancelación deshabilitado");
            return;
        }

        String phone = appointment.getCustomer().getPhone();
        if (phone == null || phone.isBlank()) {
            log.warn("[WHATSAPP-SKIP] Cliente {} no tiene teléfono configurado",
                    appointment.getCustomer().getFullName());
            return;
        }

        String motivo = resolveCancellationReason(appointment);

        log.info("[WHATSAPP] Enviando plantilla cancelacion_cita para cita {} con motivo: '{}'",
                appointment.getId(), motivo);

        WhatsAppMessageBuilderService.TemplateParameters params =
                messageBuilder.buildCancelacionParams(appointment, motivo);

        sendTemplateSync(
                phone,
                config.getCancelacionCitaTemplateName(),
                config.getCitaConfirmadaLanguageCode(),
                params.headerParam(),
                params.bodyParams(),
                appointment
        );
    }

    /**
     * Resuelve el motivo y origen de la cancelación a partir de las observaciones de la cita.
     */
    public String resolveCancellationReason(Appointment appointment) {
        return messageBuilder.resolveCancellationReason(appointment);
    }

    /**
     * Envía la plantilla valoracion_servicio_google de Meta para solicitar reseña en Google.
     * BODY {{1}}=cliente, BODY {{2}}=servicio
     */
    @Async
    public void sendValoracionServicioGoogleTemplate(Appointment appointment) {
        sendValoracionServicioGoogleTemplateSync(appointment);
    }

    public boolean sendValoracionServicioGoogleTemplateSync(Appointment appointment) {
        if (!config.isUseTemplateForReview()) {
            log.info("[WHATSAPP-SKIP] Template de valoración de Google deshabilitado");
            return false;
        }

        if (appointment == null || appointment.getCustomer() == null) {
            log.warn("[WHATSAPP-SKIP] Cita o cliente nulo");
            return false;
        }

        String phone = appointment.getCustomer().getPhone();
        if (phone == null || phone.isBlank()) {
            log.warn("[WHATSAPP-SKIP] Cliente {} no tiene teléfono configurado",
                    appointment.getCustomer().getFullName());
            return false;
        }

        String cliente = appointment.getCustomer().getFullName();
        String servicio = appointment.getService() != null && appointment.getService().getName() != null
                ? appointment.getService().getName()
                : "Servicio";

        return sendTemplateSync(
                phone,
                config.getReviewTemplateName(),
                config.getCitaConfirmadaLanguageCode(),
                null,
                Arrays.asList(cliente, servicio),
                appointment
        );
    }

    /**
     * Envía notificación al admin cuando se crea una cita desde el dashboard.
     */
    @Async
    public void sendAdminAppointmentCreatedAlert(Appointment appointment) {
        adminAlertDispatcher.sendAdminAppointmentCreatedAlert(appointment);
    }

    /**
     * Envía el template agenda_en_revision con placeholders:
     * HEADER {{1}}=cliente, BODY {{1}}=servicio, {{2}}=fecha solicitada, {{3}}=bloque
     */
    @Async
    public void sendAgendaEnRevisionTemplate(BookingRequest request) {
        if (!config.isUseTemplateForBookingRequest()) {
            log.info("[WHATSAPP-SKIP] Template de agenda en revisión deshabilitado");
            return;
        }

        String phone = request.getPhone();
        if (phone == null || phone.isBlank()) {
            log.warn("[WHATSAPP-SKIP] Solicitud {} no tiene teléfono configurado", request.getId());
            return;
        }

        WhatsAppMessageBuilderService.TemplateParameters params =
                messageBuilder.buildAgendaEnRevisionParams(request);

        sendTemplate(
                phone,
                config.getAgendaEnRevisionTemplateName(),
                config.getCitaConfirmadaLanguageCode(),
                params.headerParam(),
                params.bodyParams()
        );
    }

    /**
     * Envía el template solicitud_rechazada con placeholders:
     * HEADER {{1}}=cliente, BODY {{1}}=servicio, {{2}}=fecha solicitada, {{3}}=bloque
     */
    @Async
    public void sendSolicitudRechazadaTemplate(BookingRequest request) {
        if (!config.isUseTemplateForBookingRejection()) {
            log.info("[WHATSAPP-SKIP] Template de solicitud rechazada deshabilitado");
            return;
        }

        String phone = request.getPhone();
        if (phone == null || phone.isBlank()) {
            log.warn("[WHATSAPP-SKIP] Solicitud {} no tiene teléfono configurado", request.getId());
            return;
        }

        WhatsAppMessageBuilderService.TemplateParameters params =
                messageBuilder.buildSolicitudRechazadaParams(request);

        sendTemplate(
                phone,
                config.getSolicitudRechazadaTemplateName(),
                config.getCitaConfirmadaLanguageCode(),
                params.headerParam(),
                params.bodyParams()
        );
    }

    @Async
    public void sendLoyaltyUpdateMessage(cl.bunnycure.domain.model.Customer customer) {
        try {
            String phone = customer.getPhone();
            if (phone == null || phone.isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Cliente {} no tiene teléfono configurado para loyalty",
                        customer.getFullName());
                return;
            }

            int rewardIndex = customer.getCurrentRewardIndex() != null ? customer.getCurrentRewardIndex() : 0;
            var reward = loyaltyRewardService.getRewardAt(rewardIndex);
            String message = messageBuilder.buildLoyaltyUpdateText(customer, reward);

            sendTextMessage(phone, message);

        } catch (Exception e) {
            log.error("[WHATSAPP] Error al enviar mensaje de fidelización: {}", e.getMessage(), e);
        }
    }

    /**
     * Normaliza el número de teléfono eliminando caracteres especiales
     * y asegurando que tenga el formato correcto para WhatsApp API
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null) {
            return "";
        }
        // Eliminar espacios, guiones, paréntesis y el símbolo +
        String normalized = phone.replaceAll("[\\s\\-()]+", "");
        
        // Si el número no empieza con código de país, asumimos Chile (56)
        if (!normalized.startsWith("56") && !normalized.startsWith("+")) {
            normalized = "56" + normalized;
        }
        
        // Eliminar el + si existe
        normalized = normalized.replace("+", "");
        
        return normalized;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record MediaDownloadResult(byte[] content, String mimeType, String sha256) {
    }
}
