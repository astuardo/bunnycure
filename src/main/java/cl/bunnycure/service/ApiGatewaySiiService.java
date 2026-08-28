package cl.bunnycure.service;

import cl.bunnycure.config.ApiGatewayConfig;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.InvoiceLog;
import cl.bunnycure.domain.repository.InvoiceLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApiGatewaySiiService {

    private final ApiGatewayConfig config;
    private final InvoiceLogRepository invoiceLogRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;



    private static final String EMITIR_BHE_ENDPOINT = "/api/v2/sii/bhe/emitidas/emitir";
    private static final String EMAIL_BHE_ENDPOINT = "/api/v2/sii/bhe/emitidas/email/";
    private static final String DOCUMENTOS_BHE_ENDPOINT = "/api/v2/sii/bhe/emitidas/documentos/";
    private static final String PDF_BHE_ENDPOINT = "/api/v2/sii/bhe/emitidas/pdf/";
    private static final String ANULAR_BHE_ENDPOINT = "/api/v2/sii/bhe/emitidas/anular/";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Genera una boleta de honorarios electrónica (BHE) en el SII para una cita completada
     * y opcionalmente envía el correo oficial del SII al cliente.
     */
    @Transactional
    public Optional<String> generateInvoice(Appointment appointment, Customer customer, BigDecimal amount) {
        // Verificar si ya existe boleta para esta cita
        Optional<InvoiceLog> existingLog = invoiceLogRepository.findByAppointmentId(appointment.getId());
        if (existingLog.isPresent()) {
            log.warn("[INVOICE] Invoice already generated for appointment {}", appointment.getId());
            return Optional.ofNullable(existingLog.get().getInvoiceNumber());
        }

        // Si no está habilitado o configurado, modo dry-run (no bloquea flujo de cita)
        if (!config.isConfigured()) {
            try {
                Map<String, Object> requestBody = buildEmitirPayload(customer, amount);
                String payloadJson = objectMapper.writeValueAsString(requestBody);
                log.info("[INVOICE-DRYRUN] APIGATEWAY_ENABLED=false o credenciales faltantes. Payload simulado: {}", payloadJson);
            } catch (Exception jex) {
                log.warn("[INVOICE-DRYRUN] Error construyendo payload para dry-run: {}", jex.getMessage());
            }
            createFailedLog(appointment, customer, amount, "Dry-run: ApiGateway no configurado/deshabilitado - payload registrado en logs");
            return Optional.empty();
        }

        // Validar RUT del cliente
        if (customer.getRut() == null || customer.getRut().isBlank() || !validateRutFormat(customer.getRut())) {
            String message = "RUT del cliente inválido o no provisto: " + customer.getRut();
            log.warn("[INVOICE-ERROR] No se puede emitir BHE: {}", message);
            createFailedLog(appointment, customer, amount, message);
            return Optional.empty();
        }

        try {
            Map<String, Object> requestBody = buildEmitirPayload(customer, amount);
            String payloadJson = objectMapper.writeValueAsString(requestBody);
            HttpHeaders headers = buildAuthHeaders();

            String url = getBaseApiUrl() + EMITIR_BHE_ENDPOINT;
            HttpEntity<String> entity = new HttpEntity<>(payloadJson, headers);

            log.info("[INVOICE] Emitiendo BHE vía ApiGateway para cliente {} (RUT: {}) monto: {} -> URL: {}", 
                    customer.getId(), customer.getRut(), amount, url);
            log.info("[INVOICE-PAYLOAD] {}", payloadJson);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());

                String folio = extractFolio(json);
                String codigo = extractCodigo(json);
                String barcode = extractBarcode(json);

                log.info("[INVOICE-SUCCESS] BHE emitida exitosamente. Folio: {}, Código SII: {}", folio, codigo);

                InvoiceLog savedLog = createSuccessLog(appointment, customer, amount, folio, codigo, barcode);

                // Enviar correo oficial del SII si está habilitado y el cliente tiene email
                if (config.isSendEmailOnIssue() && codigo != null && !codigo.isBlank()
                        && customer.getEmail() != null && !customer.getEmail().isBlank()) {
                    try {
                        sendInvoiceEmail(codigo, customer.getEmail());
                        savedLog.setEmailSent(true);
                        savedLog.setEmailRecipient(customer.getEmail());
                        savedLog.setEmailSentAt(LocalDateTime.now());
                        invoiceLogRepository.save(savedLog);
                        log.info("[INVOICE-EMAIL] Correo oficial de BHE despachado vía SII a {}", customer.getEmail());
                    } catch (Exception mailEx) {
                        log.warn("[INVOICE-EMAIL-WARN] Boleta emitida pero falló envío de correo oficial: {}", mailEx.getMessage());
                    }
                }

                return Optional.ofNullable(folio);
            } else {
                String errorMsg = "ApiGateway retornó status HTTP " + response.getStatusCode();
                log.error("[INVOICE-ERROR] {}", errorMsg);
                createFailedLog(appointment, customer, amount, errorMsg);
                return Optional.empty();
            }
        } catch (org.springframework.web.client.HttpStatusCodeException httpEx) {
            String responseBody = httpEx.getResponseBodyAsString();
            String errorMsg = String.format("HTTP %s: %s", httpEx.getStatusCode(), responseBody.isBlank() ? httpEx.getMessage() : responseBody);
            log.error("[INVOICE-ERROR] Error HTTP al emitir BHE para cita {}: {}", appointment.getId(), errorMsg, httpEx);
            createFailedLog(appointment, customer, amount, errorMsg);
            return Optional.empty();
        } catch (Exception e) {
            log.error("[INVOICE-ERROR] Error inesperado al emitir BHE para cita {}: {}", appointment.getId(), e.getMessage(), e);
            createFailedLog(appointment, customer, amount, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Envía la boleta electrónica por correo oficial directo desde los servidores del SII
     */
    public boolean sendInvoiceEmail(String codigo, String recipientEmail) {
        if (!config.isConfigured()) {
            log.info("[INVOICE-EMAIL-DRYRUN] Servicio deshabilitado. No se envió email para código {}", codigo);
            return false;
        }

        String url = getBaseApiUrl() + EMAIL_BHE_ENDPOINT + codigo;

        Map<String, Object> auth = buildAuthCredentials();
        Map<String, Object> body = new HashMap<>();
        body.put("auth", auth);

        if (recipientEmail != null && !recipientEmail.isBlank()) {
            Map<String, String> destinatario = new HashMap<>();
            destinatario.put("email", recipientEmail.trim());
            body.put("destinatario", destinatario);
        }

        try {
            String payloadJson = objectMapper.writeValueAsString(body);
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<String> entity = new HttpEntity<>(payloadJson, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (org.springframework.web.client.HttpStatusCodeException httpEx) {
            log.error("[INVOICE-EMAIL-ERROR] Error HTTP enviando email BHE {} (HTTP {}): {}", 
                    codigo, httpEx.getStatusCode(), httpEx.getResponseBodyAsString());
            throw new RuntimeException("Error enviando email BHE vía SII: " + httpEx.getResponseBodyAsString(), httpEx);
        } catch (Exception e) {
            log.error("[INVOICE-EMAIL-ERROR] Error enviando email de BHE {}: {}", codigo, e.getMessage());
            throw new RuntimeException("Error enviando email BHE vía SII: " + e.getMessage(), e);
        }
    }

    /**
     * Consulta el listado de boletas de honorarios emitidas en un período (YYYYMM o YYYYMMDD)
     */
    public JsonNode listIssuedInvoices(String periodo, int pagina) {
        if (!config.isConfigured()) {
            throw new IllegalStateException("ApiGateway no está configurado");
        }

        String emisor = sanitizeRut(config.getSiiRut());
        String url = getBaseApiUrl() + DOCUMENTOS_BHE_ENDPOINT + emisor + "/" + periodo + "?pagina=" + Math.max(1, pagina);

        Map<String, Object> body = new HashMap<>();
        body.put("auth", buildAuthCredentials());

        try {
            String payloadJson = objectMapper.writeValueAsString(body);
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<String> entity = new HttpEntity<>(payloadJson, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("[INVOICE-LIST-ERROR] Error listando BHEs para período {}: {}", periodo, e.getMessage());
            throw new RuntimeException("Error consultando listado de BHEs: " + e.getMessage(), e);
        }
    }

    /**
     * Descarga el archivo binario PDF oficial de la boleta desde el SII
     */
    public byte[] getInvoicePdf(String codigo) {
        if (!config.isConfigured()) {
            throw new IllegalStateException("ApiGateway no está configurado");
        }

        String url = getBaseApiUrl() + PDF_BHE_ENDPOINT + codigo;

        Map<String, Object> body = new HashMap<>();
        body.put("auth", buildAuthCredentials());

        try {
            String payloadJson = objectMapper.writeValueAsString(body);
            HttpHeaders headers = buildAuthHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL));

            HttpEntity<String> entity = new HttpEntity<>(payloadJson, headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.POST, entity, byte[].class);
            return response.getBody();
        } catch (Exception e) {
            log.error("[INVOICE-PDF-ERROR] Error descargando PDF BHE código {}: {}", codigo, e.getMessage());
            throw new RuntimeException("Error descargando PDF de BHE: " + e.getMessage(), e);
        }
    }

    /**
     * Anula una boleta de honorarios emitida previamente en el SII
     * @param folio Folio de la boleta a anular
     * @param causa Causa de anulación: "1" (no pago), "2" (no prestado), "3" (error digitación)
     */
    public JsonNode cancelInvoice(Long folio, String causa) {
        if (!config.isConfigured()) {
            throw new IllegalStateException("ApiGateway no está configurado");
        }

        String emisor = sanitizeRut(config.getSiiRut());
        String causeCode = (causa != null && !causa.isBlank()) ? causa : "3";
        String url = getBaseApiUrl() + ANULAR_BHE_ENDPOINT + emisor + "/" + folio + "?causa=" + causeCode;

        Map<String, Object> body = new HashMap<>();
        body.put("auth", buildAuthCredentials());

        try {
            String payloadJson = objectMapper.writeValueAsString(body);
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<String> entity = new HttpEntity<>(payloadJson, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("[INVOICE-CANCEL-ERROR] Error anulando BHE folio {}: {}", folio, e.getMessage());
            throw new RuntimeException("Error al anular BHE: " + e.getMessage(), e);
        }
    }

    public String getBaseApiUrl() {
        String base = config.getEndpoint() != null && !config.getEndpoint().isBlank()
                ? config.getEndpoint().trim()
                : "https://app.apigateway.cl";
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/api/v2")) {
            base = base.substring(0, base.length() - 7);
        }
        return base;
    }


    public long getGeneratedInvoicesThisMonth() {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate firstDayNextMonth = firstDay.plusMonths(1);
        LocalDateTime start = firstDay.atStartOfDay();
        LocalDateTime end = firstDayNextMonth.atStartOfDay();
        return invoiceLogRepository.countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan("SUCCESS", start, end);
    }

    // =========================================================================
    // PAYLOAD BUILDERS Y HELPERS
    // =========================================================================

    private Map<String, Object> buildEmitirPayload(Customer customer, BigDecimal amount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("auth", buildAuthCredentials());

        Map<String, Object> boleta = new HashMap<>();

        Map<String, Object> encabezado = new HashMap<>();
        Map<String, Object> idDoc = new HashMap<>();
        idDoc.put("FchEmis", LocalDate.now().format(DATE_FORMATTER));
        idDoc.put("TipoRetencion", 2); // 2: Retención la hace el emisor (contribuyente de 2da categoría)
        encabezado.put("IdDoc", idDoc);

        Map<String, Object> emisor = new HashMap<>();
        emisor.put("RUTEmisor", sanitizeRut(config.getSiiRut()));
        encabezado.put("Emisor", emisor);

        Map<String, Object> receptor = new HashMap<>();
        receptor.put("RUTRecep", sanitizeRut(customer.getRut()));
        receptor.put("RznSocRecep", customer.getFullName() != null ? customer.getFullName() : "Cliente BunnyCure");
        receptor.put("DirRecep", "Santiago");
        receptor.put("CmnaRecep", "Santiago");
        encabezado.put("Receptor", receptor);

        boleta.put("Encabezado", encabezado);

        Map<String, Object> detalleItem = new HashMap<>();
        detalleItem.put("NmbItem", "Servicios de estética BunnyCure");
        detalleItem.put("MontoItem", amount != null ? amount.longValue() : 0L);

        boleta.put("Detalle", List.of(detalleItem));

        payload.put("boleta", boleta);
        return payload;
    }

    private Map<String, Object> buildAuthCredentials() {
        Map<String, Object> auth = new HashMap<>();
        Map<String, String> pass = new HashMap<>();
        pass.put("rut", sanitizeRut(config.getSiiRut()));
        pass.put("clave", config.getSiiPassword() != null ? config.getSiiPassword() : "");
        auth.put("pass", pass);
        return auth;
    }

    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (config.getToken() != null && !config.getToken().isBlank()) {
            String token = config.getToken().trim();
            if (token.startsWith("Token ") || token.startsWith("Bearer ")) {
                headers.set("Authorization", token);
            } else {
                headers.set("Authorization", "Token " + token);
            }
        }
        return headers;
    }


    private String extractFolio(JsonNode json) {
        if (json.has("folio")) return json.get("folio").asText();
        if (json.has("invoiceNumber")) return json.get("invoiceNumber").asText();
        if (json.has("boleta") && json.get("boleta").has("folio")) return json.get("boleta").get("folio").asText();
        if (json.has("Encabezado") && json.get("Encabezado").has("IdDoc") && json.get("Encabezado").get("IdDoc").has("Folio")) {
            return json.get("Encabezado").get("IdDoc").get("Folio").asText();
        }
        if (json.has("data") && json.get("data").has("folio")) return json.get("data").get("folio").asText();
        return "N/A";
    }

    private String extractCodigo(JsonNode json) {
        if (json.has("codigo")) return json.get("codigo").asText();
        if (json.has("boleta") && json.get("boleta").has("codigo")) return json.get("boleta").get("codigo").asText();
        if (json.has("Encabezado") && json.get("Encabezado").has("IdDoc") && json.get("Encabezado").get("IdDoc").has("Codigo")) {
            return json.get("Encabezado").get("IdDoc").get("Codigo").asText();
        }
        if (json.has("data") && json.get("data").has("codigo")) return json.get("data").get("codigo").asText();
        return null;
    }

    private String extractBarcode(JsonNode json) {
        if (json.has("codigo_barras")) return json.get("codigo_barras").asText();
        if (json.has("boleta") && json.get("boleta").has("codigo_barras")) return json.get("boleta").get("codigo_barras").asText();
        return null;
    }

    // =========================================================================
    // RUT VALIDATION & SANITIZATION
    // =========================================================================

    public String sanitizeRut(String rut) {
        if (rut == null) return "";
        String cleaned = rut.replaceAll("[.\\s]", "").toUpperCase();
        if (!cleaned.contains("-") && cleaned.length() > 1) {
            String number = cleaned.substring(0, cleaned.length() - 1);
            String dv = cleaned.substring(cleaned.length() - 1);
            cleaned = number + "-" + dv;
        }
        return cleaned;
    }

    public boolean validateRutFormat(String rut) {
        if (rut == null || rut.isBlank()) {
            return false;
        }
        String sanitized = sanitizeRut(rut);
        if (!sanitized.matches("^\\d{7,9}-[0-9K]$")) {
            return false;
        }
        String[] parts = sanitized.split("-");
        if (parts.length != 2) return false;
        String number = parts[0];
        String expectedDv = parts[1];
        return calculateRutCheckDigit(number).equalsIgnoreCase(expectedDv);
    }

    public String calculateRutCheckDigit(String rutNumberOnly) {
        String cleaned = rutNumberOnly.replaceAll("[^0-9]", "");
        if (cleaned.length() < 7) {
            return "";
        }
        int multiplier = 2;
        int sum = 0;
        for (int i = cleaned.length() - 1; i >= 0; i--) {
            sum += Integer.parseInt(String.valueOf(cleaned.charAt(i))) * multiplier;
            multiplier = (multiplier == 7) ? 2 : multiplier + 1;
        }
        int remainder = sum % 11;
        int checkDigit = 11 - remainder;
        if (checkDigit == 11) return "0";
        if (checkDigit == 10) return "K";
        return String.valueOf(checkDigit);
    }

    // =========================================================================
    // LOG PERSISTENCE
    // =========================================================================

    @Transactional
    protected InvoiceLog createSuccessLog(Appointment appointment, Customer customer, BigDecimal amount,
                                          String invoiceNumber, String siiCode, String siiBarcode) {
        InvoiceLog logEntry = InvoiceLog.builder()
                .appointment(appointment)
                .customer(customer)
                .amountInClp(amount)
                .invoiceNumber(invoiceNumber)
                .siiCode(siiCode)
                .siiBarcode(siiBarcode)
                .description("Boleta de honorarios SII - Servicios de estética BunnyCure")
                .status("SUCCESS")
                .build();
        return invoiceLogRepository.save(logEntry);
    }

    @Transactional
    protected void createFailedLog(Appointment appointment, Customer customer, BigDecimal amount, String errorMessage) {
        InvoiceLog logEntry = InvoiceLog.builder()
                .appointment(appointment)
                .customer(customer)
                .amountInClp(amount)
                .description("Boleta de honorarios SII - Servicios de estética BunnyCure")
                .status("FAILED")
                .errorMessage(errorMessage)
                .build();
        invoiceLogRepository.save(logEntry);
    }
}
