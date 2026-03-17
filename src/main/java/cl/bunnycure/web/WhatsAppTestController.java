package cl.bunnycure.web;

import cl.bunnycure.config.WhatsAppConfig;
import cl.bunnycure.service.WhatsAppService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller temporal para probar la integración de WhatsApp.
 * 
 * IMPORTANTE: Este controller es solo para pruebas durante desarrollo.
 * Eliminar o proteger con autenticación antes de desplegar a producción.
 * 
 * Ejemplos de uso:
 * 
 * POST http://localhost:8080/api/test/whatsapp/send
 * Content-Type: application/json
 * {
 *   "phone": "56912345678",
 *   "message": "Hola desde BunnyCure! 🐰"
 * }
 */
@Slf4j
@RestController
@RequestMapping("/api/test/whatsapp")
@Profile("local")
@RequiredArgsConstructor
public class WhatsAppTestController {
    
    private final WhatsAppService whatsAppService;
    private final WhatsAppConfig whatsAppConfig;

    /**
     * Endpoint para enviar un mensaje de prueba
     * 
     * curl -X POST http://localhost:8080/api/test/whatsapp/send \
     *   -H "Content-Type: application/json" \
     *   -d "{\"phone\":\"56912345678\",\"message\":\"Hola desde BunnyCure\"}"
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> sendTestMessage(@RequestBody TestMessageRequest request) {
        log.info("[TEST] Enviando mensaje de prueba a: {}", request.getPhone());
        
        try {
            whatsAppService.sendTextMessage(request.getPhone(), request.getMessage());
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Mensaje enviado. Revisa los logs para confirmar.");
            response.put("phone", request.getPhone());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("[TEST] Error al enviar mensaje: {}", e.getMessage());
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Endpoint para verificar la configuración
     * 
     * GET http://localhost:8080/api/test/whatsapp/config
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> checkConfig() {
        Map<String, String> config = new HashMap<>();
        
        // Leer desde WhatsAppConfig (funciona con properties y variables de entorno)
        String token = whatsAppConfig.getToken();
        String phoneId = whatsAppConfig.getPhoneId();
        
        boolean tokenConfigured = token != null && !token.isEmpty();
        boolean phoneIdConfigured = phoneId != null && !phoneId.isEmpty();
        
        config.put("phoneId", phoneIdConfigured ? phoneId : "Not configured");
        config.put("hasToken", tokenConfigured ? "true" : "false");
        config.put("tokenPreview", tokenConfigured ? token.substring(0, Math.min(10, token.length())) + "..." : "Not configured");
        config.put("apiVersion", "v22.0");
        config.put("status", (tokenConfigured && phoneIdConfigured) ? "Ready" : "Not configured");
        
        return ResponseEntity.ok(config);
    }

    /**
     * Endpoint para enviar mensajes de prueba predefinidos
     * 
     * POST http://localhost:8080/api/test/whatsapp/templates/{type}?phone=56912345678
     */
    @PostMapping("/templates/{type}")
    public ResponseEntity<Map<String, String>> sendTemplateMessage(
            @PathVariable String type,
            @RequestParam String phone) {
        
        String message;
        
        switch (type.toLowerCase()) {
            case "bienvenida":
                message = "¡Bienvenida a BunnyCure! 🐰✨\n\n" +
                         "Gracias por confiar en nosotros. Estamos aquí para cuidar de tus uñas.\n\n" +
                         "Agenda tu cita en www.bunnycure.cl";
                break;
                
            case "confirmacion":
                message = "💅 *Tu cita está confirmada - BunnyCure*\n\n" +
                         "Fecha: [EJEMPLO] Martes 10 de marzo de 2026\n" +
                         "Hora: 14:00\n" +
                         "Servicio: Manicure Clásica\n\n" +
                         "¡Te esperamos! 🐇";
                break;
                
            case "recordatorio":
                message = "⏰ *Recordatorio de cita - BunnyCure*\n\n" +
                         "Te recordamos tu cita de mañana:\n\n" +
                         "Fecha: [EJEMPLO]\n" +
                         "Hora: [EJEMPLO]\n\n" +
                         "Nos vemos pronto! 🐇✨";
                break;
                
            case "cancelacion":
                message = "❌ *Cita cancelada - BunnyCure*\n\n" +
                         "Tu cita ha sido cancelada.\n\n" +
                         "Si deseas reagendar, visita nuestra web.\n\n" +
                         "Saludos! 🐇";
                break;
                
            default:
                Map<String, String> error = new HashMap<>();
                error.put("status", "error");
                error.put("message", "Tipo de template no válido. Usa: bienvenida, confirmacion, recordatorio, cancelacion");
                return ResponseEntity.badRequest().body(error);
        }
        
        whatsAppService.sendTextMessage(phone, message);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("template", type);
        response.put("phone", phone);
        response.put("message", "Template enviado. Revisa logs.");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint para probar el template hello_world (el mismo que funciona en el curl de Meta)
     * 
     * POST http://localhost:8080/api/test/whatsapp/template/hello_world?phone=56983692046
     */
    @PostMapping("/template/hello_world")
    public ResponseEntity<Map<String, String>> sendHelloWorldTemplate(@RequestParam String phone) {
        log.info("[TEST] Enviando template hello_world a: {}", phone);
        
        try {
            whatsAppService.sendTemplate(phone, "hello_world", "en_US");
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("template", "hello_world");
            response.put("phone", phone);
            response.put("message", "Template enviado. Revisa logs y tu WhatsApp.");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("[TEST] Error al enviar template: {}", e.getMessage());
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Endpoint para probar template productivo cita_confirmada con parámetros.
     *
     * POST /api/test/whatsapp/template/cita_confirmada?phone=569...&customerName=Ana&businessName=BunnyCure&service=Manicure&date=10/03/2026&time=14:00
     */
    @PostMapping("/template/cita_confirmada")
    public ResponseEntity<Map<String, String>> sendCitaConfirmadaTemplate(
            @RequestParam String phone,
            @RequestParam(defaultValue = "Cliente") String customerName,
            @RequestParam(defaultValue = "BunnyCure") String businessName,
            @RequestParam(defaultValue = "Manicure") String service,
            @RequestParam(defaultValue = "10/03/2026") String date,
            @RequestParam(defaultValue = "14:00") String time) {
        log.info("[TEST] Enviando template cita_confirmada a: {}", phone);
        try {
            whatsAppService.sendTemplate(
                    phone,
                    whatsAppConfig.getCitaConfirmadaTemplateName(),
                    whatsAppConfig.getCitaConfirmadaLanguageCode(),
                    java.util.Arrays.asList(customerName, businessName, service, date, time)
            );

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("template", whatsAppConfig.getCitaConfirmadaTemplateName());
            response.put("phone", phone);
            response.put("message", "Template cita_confirmada enviado. Revisa logs y WhatsApp.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[TEST] Error al enviar template cita_confirmada: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Endpoint de diagnóstico con respuesta completa de la API
     * 
     * POST http://localhost:8080/api/test/whatsapp/diagnose?phone=56983692046&message=Prueba
     */
    @PostMapping("/diagnose")
    public ResponseEntity<Map<String, Object>> diagnoseWhatsAppSend(
            @RequestParam String phone,
            @RequestParam(defaultValue = "Mensaje de prueba desde BunnyCure 🐇") String message) {
        
        log.info("[TEST-DIAGNOSE] Iniciando diagnóstico para número: {}", phone);
        
        Map<String, Object> diagnosticInfo = new HashMap<>();
        diagnosticInfo.put("phoneInputMasked", maskPhone(phone));
        diagnosticInfo.put("messageLength", message != null ? message.length() : 0);
        diagnosticInfo.put("timestamp", java.time.LocalDateTime.now().toString());
        
        // Verificar configuración
        String token = whatsAppConfig.getToken();
        String phoneId = whatsAppConfig.getPhoneId();
        
        diagnosticInfo.put("tokenConfigured", token != null && !token.isEmpty());
        diagnosticInfo.put("phoneIdConfigured", phoneId != null && !phoneId.isEmpty());
        
        if (token == null || token.isEmpty() || phoneId == null || phoneId.isEmpty()) {
            return ResponseEntity.status(500).body(buildSafeDiagnosticError(
                    "error",
                    "WhatsApp no está configurado correctamente",
                    null
            ));
        }
        
        try {
            // Normalizar número
            String normalizedPhone = phone.replaceAll("[\\s\\-()]+", "");
            if (!normalizedPhone.startsWith("56") && !normalizedPhone.startsWith("+")) {
                normalizedPhone = "56" + normalizedPhone;
            }
            normalizedPhone = normalizedPhone.replace("+", "");
            
            diagnosticInfo.put("phoneNormalized", normalizedPhone);
            
            // Construir URL y payload
            String url = String.format("https://graph.facebook.com/v22.0/%s/messages", phoneId);
            diagnosticInfo.put("apiUrl", url);
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", normalizedPhone);
            payload.put("type", "text");
            
            Map<String, String> text = new HashMap<>();
            text.put("body", message);
            payload.put("text", text);
            
            diagnosticInfo.put("payload", payload);
            
            // Configurar headers
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);
            
            org.springframework.http.HttpEntity<Map<String, Object>> request = 
                new org.springframework.http.HttpEntity<>(payload, headers);
            
            // Enviar petición
            log.info("[TEST-DIAGNOSE] Enviando petición a WhatsApp API...");
            org.springframework.http.ResponseEntity<String> response = 
                new org.springframework.web.client.RestTemplate().exchange(
                    url,
                    org.springframework.http.HttpMethod.POST,
                    request,
                    String.class
                );
            
            // Capturar respuesta completa
            diagnosticInfo.put("httpStatus", response.getStatusCode().value());
            diagnosticInfo.put("httpStatusText", response.getStatusCode().toString());
            diagnosticInfo.put("responseBody", response.getBody());
            diagnosticInfo.put("responseHeaders", response.getHeaders().toString());
            
            if (response.getStatusCode().is2xxSuccessful()) {
                diagnosticInfo.put("status", "success");
                diagnosticInfo.put("message", "Mensaje enviado a WhatsApp API");
                diagnosticInfo.put("important", "Si el mensaje NO llega al WhatsApp, el número debe estar verificado en Meta for Developers");
                diagnosticInfo.put("guide", "Lee WHATSAPP_MENSAJES_NO_LLEGAN.md para más información");
            } else {
                diagnosticInfo.put("status", "error");
                diagnosticInfo.put("message", "WhatsApp API devolvió error");
            }
            
            return ResponseEntity.ok(diagnosticInfo);
            
        } catch (Exception e) {
            log.error("[TEST-DIAGNOSE] Error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(buildSafeDiagnosticError(
                    "exception",
                    "Error ejecutando diagnóstico de WhatsApp",
                    e
            ));
        }
    }

    private Map<String, Object> buildSafeDiagnosticError(String status, String errorMessage, Exception exception) {
        Map<String, Object> safeError = new HashMap<>();
        safeError.put("status", status);
        safeError.put("error", errorMessage);
        safeError.put("timestamp", java.time.LocalDateTime.now().toString());
        if (exception != null) {
            safeError.put("errorType", exception.getClass().getSimpleName());
        }
        return safeError;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digitsOnly = phone.replaceAll("\\D", "");
        if (digitsOnly.length() <= 4) {
            return "****";
        }
        return "****" + digitsOnly.substring(digitsOnly.length() - 4);
    }

    // DTO para la petición
    @Getter
    @Setter
    static class TestMessageRequest {
        private String phone;
        private String message;
    }
}
