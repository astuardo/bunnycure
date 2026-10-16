package cl.bunnycure.service;

import cl.bunnycure.config.WhatsAppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Gestión de la cuenta de WhatsApp Business en Meta (Graph API): plantillas de mensajes,
 * salud del número, perfil de empresa, analíticas y descarga de archivos multimedia recibidos.
 *
 * Separado de {@link WhatsAppService}, que queda dedicado al envío de mensajes a clientas.
 */
@Slf4j
@Service
public class WhatsAppBusinessApiService {

    private static final String WHATSAPP_API_URL = "https://graph.facebook.com/v22.0";

    private final WhatsAppConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WhatsAppBusinessApiService(WhatsAppConfig config, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
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

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record MediaDownloadResult(byte[] content, String mimeType, String sha256) {
    }
}
