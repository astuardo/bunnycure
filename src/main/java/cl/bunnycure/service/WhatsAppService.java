package cl.bunnycure.service;

import cl.bunnycure.config.WhatsAppConfig;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.BookingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio para enviar mensajes mediante WhatsApp Cloud API.
 * Documentación: https://developers.facebook.com/docs/whatsapp/cloud-api
 */
@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);
    private static final String WHATSAPP_API_URL = "https://graph.facebook.com/v22.0";
    
    private final WhatsAppConfig config;
    private final RestTemplate restTemplate;

    public WhatsAppService(WhatsAppConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
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

            ResponseEntity<Map> metadataResponse = restTemplate.exchange(
                    String.format("%s/%s", WHATSAPP_API_URL, mediaId),
                    HttpMethod.GET,
                    metadataRequest,
                    Map.class
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
     * Envía un mensaje de texto simple a un número de WhatsApp
     */
    @Async
    public void sendTextMessage(String toPhoneNumber, String message) {
        sendTextMessageSync(toPhoneNumber, message);
    }

    public boolean sendTextMessageSync(String toPhoneNumber, String message) {
        try {
            if (config.getToken() == null || config.getToken().isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Token no configurado");
                return false;
            }

            if (config.getPhoneId() == null || config.getPhoneId().isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Phone ID no configurado");
                return false;
            }

            String url = String.format("%s/%s/messages", WHATSAPP_API_URL, config.getPhoneId());

            // Normalizar número de teléfono (quitar caracteres especiales)
            String normalizedPhone = normalizePhoneNumber(toPhoneNumber);

            // Construir el payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", normalizedPhone);
            payload.put("type", "text");
            
            Map<String, String> text = new HashMap<>();
            text.put("body", message);
            payload.put("text", text);

            // Configurar headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getToken());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            // Log del payload para debug
            log.debug("[WHATSAPP-DEBUG] Enviando a URL: {}", url);
            log.debug("[WHATSAPP-DEBUG] Payload: {}", payload);
            log.debug("[WHATSAPP-DEBUG] Número normalizado: {}", normalizedPhone);

            // Enviar petición
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            // Log detallado de la respuesta
            log.info("[WHATSAPP-RESPONSE] Status: {}", response.getStatusCode());
            log.info("[WHATSAPP-RESPONSE] Body: {}", response.getBody());
            log.info("[WHATSAPP-RESPONSE] Headers: {}", response.getHeaders());

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[WHATSAPP] ✅ Mensaje enviado exitosamente a {}", normalizedPhone);
                log.info("[WHATSAPP] ℹ️ IMPORTANTE: Verifica que el número {} esté registrado en Meta for Developers", normalizedPhone);
                log.info("[WHATSAPP] ℹ️ Si estás en modo sandbox, solo puedes enviar a números verificados");
                return true;
            } else {
                log.error("[WHATSAPP] ❌ Error al enviar mensaje. Status: {}, Body: {}", 
                        response.getStatusCode(), response.getBody());
                return false;
            }

        } catch (Exception e) {
            log.error("[WHATSAPP] ❌ Excepción al enviar mensaje a {}: {}", toPhoneNumber, e.getMessage());
            log.error("[WHATSAPP] ℹ️ Detalles del error:", e);
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

            String fechaFormateada = LocalDateTime.of(appointment.getAppointmentDate(), appointment.getAppointmentTime())
                    .format(DateTimeFormatter.ofPattern("EEEE dd 'de' MMMM 'de' yyyy 'a las' HH:mm",
                            new Locale("es", "CL")));

            String message = String.format(
                    "💅 *Tu cita está confirmada - BunnyCure*\n\n" +
                    "Hola %s,\n\n" +
                    "Tu cita ha sido confirmada para el *%s*.\n\n" +
                    "📋 *Servicio:* %s\n" +
                    "⏱️ *Duración:* %d minutos\n\n" +
                    "Nos vemos pronto! 🐇✨",
                    appointment.getCustomer().getFullName(),
                    fechaFormateada,
                    appointment.getService().getName(),
                    appointment.getService().getDurationMinutes()
            );

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

            String fechaFormateada = LocalDateTime.of(appointment.getAppointmentDate(), appointment.getAppointmentTime())
                    .format(DateTimeFormatter.ofPattern("EEEE dd 'de' MMMM 'de' yyyy 'a las' HH:mm",
                            new Locale("es", "CL")));

            String message = String.format(
                    "❌ *Cita cancelada - BunnyCure*\n\n" +
                    "Hola %s,\n\n" +
                    "Tu cita del *%s* ha sido cancelada.\n\n" +
                    "Si deseas agendar una nueva cita, visita nuestra web.\n\n" +
                    "Saludos! 🐇",
                    appointment.getCustomer().getFullName(),
                    fechaFormateada
            );

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

            String fechaFormateada = LocalDateTime.of(appointment.getAppointmentDate(), appointment.getAppointmentTime())
                    .format(DateTimeFormatter.ofPattern("EEEE dd 'de' MMMM 'de' yyyy 'a las' HH:mm",
                            new Locale("es", "CL")));

            String message = String.format(
                    "⏰ *Recordatorio de cita - BunnyCure*\n\n" +
                    "Hola %s,\n\n" +
                    "Te recordamos tu cita para el *%s*.\n\n" +
                    "📋 *Servicio:* %s\n" +
                    "⏱️ *Duración:* %d minutos\n\n" +
                    "Te esperamos! 🐇✨",
                    appointment.getCustomer().getFullName(),
                    fechaFormateada,
                    appointment.getService().getName(),
                    appointment.getService().getDurationMinutes()
            );

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

            String fechaFormateada = request.getPreferredDate()
                    .format(DateTimeFormatter.ofPattern("EEEE dd 'de' MMMM 'de' yyyy",
                            new Locale("es", "CL")));

            String message = String.format(
                    "🐇 *Recibimos tu solicitud - BunnyCure*\n\n" +
                    "Hola %s,\n\n" +
                    "Hemos recibido tu solicitud de reserva para el *%s* en el bloque *%s*.\n\n" +
                    "📋 *Servicio:* %s\n\n" +
                    "Estamos revisando la disponibilidad y te contactaremos pronto.\n\n" +
                    "Gracias por tu preferencia! 🐇✨",
                    request.getFullName(),
                    fechaFormateada,
                    request.getPreferredBlock(),
                    request.getService().getName()
            );

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

            String fechaFormateada = request.getPreferredDate()
                    .format(DateTimeFormatter.ofPattern("EEEE dd 'de' MMMM 'de' yyyy",
                            new Locale("es", "CL")));

            String message = String.format(
                    "❌ *Solicitud no disponible - BunnyCure*\n\n" +
                    "Hola %s,\n\n" +
                    "Lamentamos informarte que no tenemos disponibilidad para el *%s* en el bloque *%s*.\n\n" +
                    "Te invitamos a intentar con otra fecha en nuestra web.\n\n" +
                    "Saludos! 🐇",
                    request.getFullName(),
                    fechaFormateada,
                    request.getPreferredBlock()
            );

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
        try {
            if (config.getToken() == null || config.getToken().isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Token no configurado");
                return false;
            }

            if (config.getPhoneId() == null || config.getPhoneId().isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Phone ID no configurado");
                return false;
            }

            String url = String.format("%s/%s/messages", WHATSAPP_API_URL, config.getPhoneId());

            // Normalizar número de teléfono
            String normalizedPhone = normalizePhoneNumber(toPhoneNumber);

            // Construir el payload para template
            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", normalizedPhone);
            payload.put("type", "template");
            
            Map<String, Object> template = new HashMap<>();
            template.put("name", templateName);
            
            Map<String, String> language = new HashMap<>();
            language.put("code", languageCode);
            template.put("language", language);

            List<Map<String, Object>> components = new ArrayList<>();

            if (headerParam != null && !headerParam.isBlank()) {
                Map<String, Object> header = new HashMap<>();
                header.put("type", "header");

                List<Map<String, String>> headerParameters = new ArrayList<>();
                Map<String, String> p = new HashMap<>();
                p.put("type", "text");
                p.put("text", headerParam);
                headerParameters.add(p);

                header.put("parameters", headerParameters);
                components.add(header);
            }

            if (bodyParams != null && !bodyParams.isEmpty()) {
                Map<String, Object> body = new HashMap<>();
                body.put("type", "body");

                List<Map<String, String>> parameters = new ArrayList<>();
                for (String value : bodyParams) {
                    Map<String, String> p = new HashMap<>();
                    p.put("type", "text");
                    p.put("text", value != null ? value : "");
                    parameters.add(p);
                }
                body.put("parameters", parameters);
                components.add(body);
            }

            if (!components.isEmpty()) {
                template.put("components", components);
            }
            
            payload.put("template", template);

            // Configurar headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getToken());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            // Log del payload para debug
            log.debug("[WHATSAPP-TEMPLATE] Enviando template '{}' a URL: {}", templateName, url);
            log.debug("[WHATSAPP-TEMPLATE] Payload: {}", payload);
            log.debug("[WHATSAPP-TEMPLATE] Número normalizado: {}", normalizedPhone);

            // Enviar petición
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            // Log detallado de la respuesta
            log.info("[WHATSAPP-TEMPLATE] Status: {}", response.getStatusCode());
            log.info("[WHATSAPP-TEMPLATE] Body: {}", response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[WHATSAPP] ✅ Template '{}' enviado exitosamente a {}", templateName, normalizedPhone);
                return true;
            } else {
                log.error("[WHATSAPP] ❌ Error al enviar template. Status: {}, Body: {}", 
                        response.getStatusCode(), response.getBody());
                return false;
            }

        } catch (Exception e) {
            log.error("[WHATSAPP] ❌ Excepción al enviar template '{}' a {}: {}", templateName, toPhoneNumber, e.getMessage());
            log.error("[WHATSAPP] ℹ️ Detalles del error:", e);
            return false;
        }
    }

    public boolean sendAdminBookingAlertSync(String toPhoneNumber, BookingRequest request) {
        if (request == null) {
            return false;
        }

        String fecha = request.getPreferredDate() != null
                ? request.getPreferredDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("es", "CL")))
                : "-";
        String servicio = request.getService() != null && request.getService().getName() != null
                ? request.getService().getName()
                : "-";
        String cliente = request.getFullName() != null && !request.getFullName().isBlank()
                ? request.getFullName().trim()
                : "-";
        String bloque = request.getPreferredBlock() != null && !request.getPreferredBlock().isBlank()
                ? request.getPreferredBlock().trim()
                : "-";

        String adminTemplate = config.getAdminBookingAlertTemplateName();
        if (config.isUseTemplateForAdminAlert() && adminTemplate != null && !adminTemplate.isBlank()) {
            boolean templateSent = sendTemplateSync(
                    toPhoneNumber,
                    adminTemplate,
                    resolveAdminAlertLanguageCode(),
                    cliente,
                    Arrays.asList(servicio, fecha, bloque)
            );
            if (templateSent) {
                log.info("[WHATSAPP-ADMIN] ✅ Alerta enviada por template a {}", normalizePhoneNumber(toPhoneNumber));
                return true;
            }
            log.warn("[WHATSAPP-ADMIN] ⚠️ Fallo envío por template admin, se intentará texto personalizado");
        }

        boolean textSent = sendTextMessageSync(toPhoneNumber, buildAdminAlertText(request));
        if (textSent) {
            return true;
        }

        if (config.isUseTemplateForBookingRequest()) {
            log.warn("[WHATSAPP-ADMIN] ⚠️ Fallo texto personalizado, se intentará template de respaldo '{}'", config.getAgendaEnRevisionTemplateName());
            return sendTemplateSync(
                    toPhoneNumber,
                    config.getAgendaEnRevisionTemplateName(),
                    config.getCitaConfirmadaLanguageCode(),
                    cliente,
                    Arrays.asList(servicio, fecha, bloque)
            );
        }

        return false;
    }

    /**
     * Envía el template confirmacion_cita con placeholders:
     * HEADER {{1}}=cliente, BODY {{1}}=servicio, {{2}}=fecha, {{3}}=hora
     */
    @Async
    public void sendCitaConfirmadaTemplate(Appointment appointment) {
        String phone = appointment.getCustomer().getPhone();
        if (phone == null || phone.isBlank()) {
            log.warn("[WHATSAPP-SKIP] Cliente {} no tiene teléfono configurado",
                    appointment.getCustomer().getFullName());
            return;
        }

        String fecha = appointment.getAppointmentDate()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("es", "CL")));
        String hora = appointment.getAppointmentTime()
                .format(DateTimeFormatter.ofPattern("HH:mm"));
        String servicio = appointment.getService().getName();
        String cliente = appointment.getCustomer().getFullName();

        sendTemplate(
                phone,
                config.getCitaConfirmadaTemplateName(),
                config.getCitaConfirmadaLanguageCode(),
                cliente,
                Arrays.asList(servicio, fecha, hora)
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

        String fecha = appointment.getAppointmentDate()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("es", "CL")));
        String hora = appointment.getAppointmentTime()
                .format(DateTimeFormatter.ofPattern("HH:mm"));
        String servicio = appointment.getService().getName();
        String cliente = appointment.getCustomer().getFullName();

        sendTemplate(
                phone,
                config.getRecordatorioCitaTemplateName(),
                config.getCitaConfirmadaLanguageCode(),
                cliente,
                Arrays.asList(servicio, fecha, hora)
        );
    }

    /**
     * Envía el template cancelacion_cita con placeholders:
     * HEADER {{1}}=cliente, BODY {{1}}=servicio, {{2}}=fecha, {{3}}=hora
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

        String fecha = appointment.getAppointmentDate()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("es", "CL")));
        String hora = appointment.getAppointmentTime()
                .format(DateTimeFormatter.ofPattern("HH:mm"));
        String servicio = appointment.getService().getName();
        String cliente = appointment.getCustomer().getFullName();

        sendTemplate(
                phone,
                config.getCancelacionCitaTemplateName(),
                config.getCitaConfirmadaLanguageCode(),
                cliente,
                Arrays.asList(servicio, fecha, hora)
        );
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

        String fecha = request.getPreferredDate()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("es", "CL")));
        String servicio = request.getService().getName();
        String cliente = request.getFullName();
        String bloque = request.getPreferredBlock();

        sendTemplate(
                phone,
                config.getAgendaEnRevisionTemplateName(),
                config.getCitaConfirmadaLanguageCode(),
                cliente,
                Arrays.asList(servicio, fecha, bloque)
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

        String fecha = request.getPreferredDate()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("es", "CL")));
        String servicio = request.getService().getName();
        String cliente = request.getFullName();
        String bloque = request.getPreferredBlock();

        sendTemplate(
                phone,
                config.getSolicitudRechazadaTemplateName(),
                config.getCitaConfirmadaLanguageCode(),
                cliente,
                Arrays.asList(servicio, fecha, bloque)
        );
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

    private String resolveAdminAlertLanguageCode() {
        String configured = config.getAdminBookingAlertLanguageCode();
        if (configured == null || configured.isBlank()) {
            return config.getCitaConfirmadaLanguageCode();
        }
        return configured;
    }

    private String buildAdminAlertText(BookingRequest request) {
        String serviceName = request.getService() != null && request.getService().getName() != null
                ? request.getService().getName()
                : "(sin servicio)";
        String preferredDate = request.getPreferredDate() != null
                ? request.getPreferredDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("es", "CL")))
                : "(sin fecha)";
        String preferredBlock = formatPreferredBlock(request.getPreferredBlock());
        String notes = request.getNotes() != null && !request.getNotes().isBlank()
                ? request.getNotes().trim()
                : "-";
        String reviewLink = buildAdminReviewLink(request.getId());

        return String.format(
                "NUEVA SOLICITUD - BunnyCure\n\n" +
                        "%s solicitó una hora.\n" +
                        "Revisa disponibilidad en Solicitudes y confirma/reagenda.\n\n" +
                        "ID solicitud: %s\n" +
                        "Cliente: %s\n" +
                        "Telefono: %s\n" +
                        "Email: %s\n" +
                        "Servicio: %s\n" +
                        "Fecha preferida: %s\n" +
                        "Bloque: %s\n" +
                        "Notas: %s\n\n" +
                        "Revisar ahora: %s",
                safeValue(request.getFullName()),
                request.getId(),
                safeValue(request.getFullName()),
                safeValue(request.getPhone()),
                safeValue(request.getEmail()),
                serviceName,
                preferredDate,
                preferredBlock,
                notes,
                reviewLink
        );
    }

    private String formatPreferredBlock(String rawBlock) {
        if (rawBlock == null || rawBlock.isBlank()) {
            return "(sin bloque)";
        }
        String normalized = rawBlock.trim();
        return switch (normalized.toUpperCase(Locale.ROOT)) {
            case "MORNING" -> "Manana";
            case "AFTERNOON" -> "Tarde";
            case "NIGHT" -> "Noche";
            default -> normalized;
        };
    }

    private String buildAdminReviewLink(Long bookingRequestId) {
        String baseUrl = config.getAdminBookingRequestsUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return "Panel admin -> /admin/booking-requests";
        }
        String trimmed = baseUrl.trim();
        if (bookingRequestId == null) {
            return trimmed;
        }
        return trimmed.endsWith("/") ? trimmed + bookingRequestId : trimmed + "/" + bookingRequestId;
    }

    private String safeValue(String value) {
        return (value == null || value.isBlank()) ? "-" : value.trim();
    }

    public record MediaDownloadResult(byte[] content, String mimeType, String sha256) {
    }
}