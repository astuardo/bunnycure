package cl.bunnycure.service;

import cl.bunnycure.service.sii.ChileanRut;
import cl.bunnycure.service.sii.SiiResponseParser;
import cl.bunnycure.service.sii.InvoiceLogMapper;

import cl.bunnycure.config.ApiGatewayConfig;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.InvoiceLog;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.domain.repository.InvoiceLogRepository;
import cl.bunnycure.web.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ApiGatewaySiiService {

    private final ApiGatewayConfig config;
    private final InvoiceLogRepository invoiceLogRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final cl.bunnycure.domain.repository.TenantRepository tenantRepository;
    private final InvoiceOutboxService invoiceOutboxService;

    @org.springframework.beans.factory.annotation.Autowired
    public ApiGatewaySiiService(
            ApiGatewayConfig config,
            InvoiceLogRepository invoiceLogRepository,
            AppointmentRepository appointmentRepository,
            CustomerRepository customerRepository,
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            cl.bunnycure.domain.repository.TenantRepository tenantRepository,
            @org.springframework.context.annotation.Lazy InvoiceOutboxService invoiceOutboxService) {
        this.config = config;
        this.invoiceLogRepository = invoiceLogRepository;
        this.appointmentRepository = appointmentRepository;
        this.customerRepository = customerRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.tenantRepository = tenantRepository;
        this.invoiceOutboxService = invoiceOutboxService;
    }

    public ApiGatewaySiiService(
            ApiGatewayConfig config,
            InvoiceLogRepository invoiceLogRepository,
            AppointmentRepository appointmentRepository,
            CustomerRepository customerRepository,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this(config, invoiceLogRepository, appointmentRepository, customerRepository, restTemplate, objectMapper, null, null);
    }

    private static final String EMITIR_BHE_ENDPOINT = "/api/v2/sii/bhe/emitidas/emitir";
    private static final String EMAIL_BHE_ENDPOINT = "/api/v2/sii/bhe/emitidas/email/";
    private static final String DOCUMENTOS_BHE_ENDPOINT = "/api/v2/sii/bhe/emitidas/documentos/";
    private static final String PDF_BHE_ENDPOINT = "/api/v2/sii/bhe/emitidas/pdf/";
    private static final String ANULAR_BHE_ENDPOINT = "/api/v2/sii/bhe/emitidas/anular/";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Caché en memoria para evitar consumo excesivo de créditos de API
    private Map<String, CachedSiiList> siiListCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 15 * 60 * 1000; // 15 minutos

    private static class CachedSiiList {
        final JsonNode data;
        final long timestamp;

        CachedSiiList(JsonNode data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_TTL_MS;
        }
    }

    /**
     * Emite BHE de forma asíncrona en segundo plano para no bloquear el hilo HTTP
     * de la solicitud web ni causar timeout H12 en Heroku.
     */
    @Async
    public void generateInvoiceAsync(Appointment appointment, Customer customer, BigDecimal amount) {
        try {
            if (invoiceOutboxService != null) {
                invoiceOutboxService.enqueueInvoice(appointment, customer, amount);
                log.info("[INVOICE-ASYNC] Boleta encolada en Outbox para cita {}", appointment != null ? appointment.getId() : "null");
            } else {
                generateInvoice(appointment, customer, amount);
            }
        } catch (Exception e) {
            log.error("[INVOICE-ASYNC-ERROR] Error en emisión asíncrona de BHE para cita {}: {}",
                    appointment != null ? appointment.getId() : "null", e.getMessage(), e);
        }
    }

    public boolean isSiiConfiguredForCurrentTenant() {
        Long currentTenantId = cl.bunnycure.config.TenantContext.getCurrentTenantId();
        if (tenantRepository != null && currentTenantId != null) {
            Optional<cl.bunnycure.domain.model.Tenant> tenantOpt = tenantRepository.findById(currentTenantId);
            if (tenantOpt.isPresent()) {
                cl.bunnycure.domain.model.Tenant t = tenantOpt.get();
                if (t.getSiiRut() != null && !t.getSiiRut().isBlank() && t.getSiiPassword() != null && !t.getSiiPassword().isBlank()) {
                    return true;
                }
            }
        }
        return config.isConfigured();
    }

    /**
     * Genera una boleta de honorarios electrónica (BHE) en el SII para una cita completada
     * y opcionalmente envía el correo oficial del SII al cliente.
     * Si ya existe un log previo con FAILED/ERROR, permite reintentar y actualiza el registro.
     */
    @Transactional
    public Optional<String> generateInvoice(Appointment appointment, Customer customer, BigDecimal amount) {

        // Verificar si ya existe boleta exitosa para esta cita
        Optional<InvoiceLog> existingLog = invoiceLogRepository.findByAppointmentId(appointment.getId());
        if (existingLog.isPresent() && "SUCCESS".equalsIgnoreCase(existingLog.get().getStatus())
                && existingLog.get().getInvoiceNumber() != null && !existingLog.get().getInvoiceNumber().isBlank()) {
            log.warn("[INVOICE] Invoice already generated with SUCCESS for appointment {}", appointment.getId());
            return Optional.ofNullable(existingLog.get().getInvoiceNumber());
        }

        // Si no está habilitado o configurado, modo dry-run (no bloquea flujo de cita)
        if (!isSiiConfiguredForCurrentTenant()) {
            try {
                Map<String, Object> requestBody = buildEmitirPayload(appointment, customer, amount);
                String payloadJson = objectMapper.writeValueAsString(requestBody);
                log.info("[INVOICE-DRYRUN] APIGATEWAY_ENABLED=false o credenciales faltantes. Payload simulado: {}",
                        payloadJson);
            } catch (Exception jex) {
                log.warn("[INVOICE-DRYRUN] Error construyendo payload para dry-run: {}", jex.getMessage());
            }
            createFailedLog(appointment, customer, amount,
                    "Dry-run: ApiGateway no configurado/deshabilitado - payload registrado en logs");
            return Optional.empty();
        }

        // Validar RUT del cliente
        if (customer.getRut() == null || customer.getRut().isBlank() || !ChileanRut.validateRutFormat(customer.getRut())) {
            String message = "RUT del cliente inválido o no provisto: " + customer.getRut();
            log.warn("[INVOICE-ERROR] No se puede emitir BHE: {}", message);
            createFailedLog(appointment, customer, amount, message);
            return Optional.empty();
        }

        try {
            Map<String, Object> requestBody = buildEmitirPayload(appointment, customer, amount);
            String payloadJson = objectMapper.writeValueAsString(requestBody);
            HttpHeaders headers = buildAuthHeaders();

            String url = getBaseApiUrl() + EMITIR_BHE_ENDPOINT;
            HttpEntity<String> entity = new HttpEntity<>(payloadJson, headers);

            log.info("[INVOICE] Emitiendo BHE vía ApiGateway para cliente {} (RUT: {}) monto: {} -> URL: {}",
                    customer.getId(), customer.getRut(), amount, url);
            log.info("[INVOICE-PAYLOAD] {}", payloadJson);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("[INVOICE-RESPONSE] Respuesta de ApiGateway: {}", response.getBody());
                JsonNode json = objectMapper.readTree(response.getBody());

                String folio = SiiResponseParser.extractFolio(json);
                String codigo = SiiResponseParser.extractCodigo(json);
                String barcode = SiiResponseParser.extractBarcode(json);

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
                        log.warn("[INVOICE-EMAIL-WARN] Boleta emitida pero falló envío de correo oficial: {}",
                                mailEx.getMessage());
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
            String errorMsg = String.format("HTTP %s: %s", httpEx.getStatusCode(),
                    responseBody.isBlank() ? httpEx.getMessage() : responseBody);
            log.error("[INVOICE-ERROR] Error HTTP al emitir BHE para cita {}: {}", appointment.getId(), errorMsg,
                    httpEx);
            createFailedLog(appointment, customer, amount, errorMsg);
            return Optional.empty();
        } catch (Exception e) {
            log.error("[INVOICE-ERROR] Error inesperado al emitir BHE para cita {}: {}", appointment.getId(),
                    e.getMessage(), e);
            createFailedLog(appointment, customer, amount, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Emite o reintenta emitir manualmente una boleta para una cita completada,
     * permitiendo actualizar opcionalmente el RUT y correo del cliente antes de emitir.
     */
    @Transactional
    public InvoiceIssuedItemDto emitInvoiceForAppointment(Long appointmentId, String overrideRut, String overrideEmail) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada con ID: " + appointmentId));

        Customer customer = appointment.getCustomer();
        if (customer == null) {
            throw new IllegalStateException("La cita no tiene un cliente asociado");
        }

        boolean customerUpdated = false;
        if (overrideRut != null && !overrideRut.isBlank()) {
            String sanitized = ChileanRut.sanitizeRut(overrideRut);
            if (!ChileanRut.validateRutFormat(sanitized)) {
                throw new IllegalArgumentException("El RUT ingresado no es válido: " + overrideRut);
            }
            customer.setRut(sanitized);
            customerUpdated = true;
        }

        if (overrideEmail != null && !overrideEmail.isBlank()) {
            customer.setEmail(overrideEmail.trim().toLowerCase());
            customerUpdated = true;
        }

        if (customerUpdated) {
            customerRepository.save(customer);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        if (appointment.getServices() != null && !appointment.getServices().isEmpty()) {
            totalAmount = appointment.getServices().stream()
                    .map(s -> s.getPrice() != null ? BigDecimal.valueOf(s.getPrice().doubleValue()) : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0 && appointment.getService() != null && appointment.getService().getPrice() != null) {
            totalAmount = BigDecimal.valueOf(appointment.getService().getPrice().doubleValue());
        }

        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("El monto total de los servicios de la cita debe ser mayor a 0");
        }

        generateInvoice(appointment, customer, totalAmount);

        InvoiceLog logEntry = invoiceLogRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new IllegalStateException("No se pudo registrar la trazabilidad de la boleta"));

        if (!"SUCCESS".equalsIgnoreCase(logEntry.getStatus())) {
            String error = logEntry.getErrorMessage() != null ? logEntry.getErrorMessage() : "Error desconocido al emitir boleta";
            throw new RuntimeException("No se pudo emitir la boleta: " + error);
        }

        return InvoiceLogMapper.toIssuedItemDto(logEntry);
    }

    /**
     * Marca una cita como con boleta emitida manualmente (sin enviar al SII, 0 créditos).
     */
    @Transactional
    public InvoiceIssuedItemDto markInvoiceAsManual(Long appointmentId, String invoiceNumber, String notes) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada con ID: " + appointmentId));

        Customer customer = appointment.getCustomer();
        if (customer == null) {
            throw new IllegalStateException("La cita no tiene un cliente asociado");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        if (appointment.getServices() != null && !appointment.getServices().isEmpty()) {
            totalAmount = appointment.getServices().stream()
                    .map(s -> s.getPrice() != null ? BigDecimal.valueOf(s.getPrice().doubleValue()) : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0 && appointment.getService() != null && appointment.getService().getPrice() != null) {
            totalAmount = BigDecimal.valueOf(appointment.getService().getPrice().doubleValue());
        }

        String folio = (invoiceNumber != null && !invoiceNumber.isBlank())
                ? invoiceNumber.trim()
                : "MANUAL-" + appointmentId;

        String description = (notes != null && !notes.isBlank())
                ? notes.trim()
                : "Boleta emitida manualmente en plataforma SII";

        InvoiceLog logEntry = invoiceLogRepository.findByAppointmentId(appointmentId)
                .orElseGet(() -> InvoiceLog.builder()
                        .appointment(appointment)
                        .customer(customer)
                        .build());

        logEntry.setCustomer(customer);
        logEntry.setAmountInClp(totalAmount);
        logEntry.setInvoiceNumber(folio);
        logEntry.setSiiCode("MANUAL");
        logEntry.setDescription(description);
        logEntry.setStatus("SUCCESS");
        logEntry.setErrorMessage(null);
        logEntry.setEmailSent(false);

        InvoiceLog saved = invoiceLogRepository.save(logEntry);
        log.info("[INVOICE-MANUAL] Cita {} marcada manualmente como emitida con folio {}", appointmentId, folio);
        return InvoiceLogMapper.toIssuedItemDto(saved);
    }

    /**
     * Marca un conjunto de citas como emitidas manualmente en lote.
     */
    @Transactional
    public List<InvoiceIssuedItemDto> batchMarkAsManual(List<Long> appointmentIds, String initialFolio, String notes) {
        if (appointmentIds == null || appointmentIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<InvoiceIssuedItemDto> results = new ArrayList<>();
        Long currentFolioNum = null;
        if (initialFolio != null && !initialFolio.isBlank()) {
            try {
                currentFolioNum = Long.parseLong(initialFolio.trim());
            } catch (NumberFormatException ignored) {}
        }

        for (Long id : appointmentIds) {
            String folio = null;
            if (currentFolioNum != null) {
                folio = String.valueOf(currentFolioNum++);
            } else if (initialFolio != null && !initialFolio.isBlank()) {
                folio = initialFolio.trim();
            }
            results.add(markInvoiceAsManual(id, folio, notes));
        }

        return results;
    }

    /**
     * Emite un conjunto de citas al SII en lote de forma secuencial.
     */
    public Map<String, Object> batchEmitInvoices(List<Long> appointmentIds) {
        if (appointmentIds == null || appointmentIds.isEmpty()) {
            return Map.of("total", 0, "successCount", 0, "failedCount", 0, "results", List.of(), "errors", List.of());
        }

        List<InvoiceIssuedItemDto> results = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();

        for (Long id : appointmentIds) {
            try {
                InvoiceIssuedItemDto item = emitInvoiceForAppointment(id, null, null);
                results.add(item);
            } catch (Exception e) {
                log.error("[INVOICE-BATCH-ERROR] Error emitiendo boleta para cita {}: {}", id, e.getMessage());
                errors.add(Map.of(
                        "appointmentId", id,
                        "error", e.getMessage() != null ? e.getMessage() : "Error desconocido"
                ));
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("total", appointmentIds.size());
        response.put("successCount", results.size());
        response.put("failedCount", errors.size());
        response.put("results", results);
        response.put("errors", errors);
        return response;
    }

    /**
     * Envía la boleta electrónica por correo oficial directo desde los servidores
     * del SII
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
     * Utiliza caché en memoria para no consumir créditos innecesarios del ApiGateway SII.
     */
    public JsonNode listIssuedInvoices(String periodo, int pagina, boolean forceRefresh) {
        String effectivePeriodo = (periodo != null && !periodo.isBlank())
                ? periodo
                : LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        int effectivePagina = Math.max(1, pagina);
        String cacheKey = effectivePeriodo + "_" + effectivePagina;

        if (!forceRefresh) {
            CachedSiiList cached = siiListCache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                log.info("[INVOICE-CACHE] Retornando listado BHE para período {} desde caché (ahorro de créditos)", effectivePeriodo);
                return cached.data;
            }
        }

        if (!config.isConfigured()) {
            log.info("[INVOICE-DRYRUN] ApiGateway no configurado. Retornando respuesta vacía para período {}", effectivePeriodo);
            JsonNode dryRunResponse = objectMapper.createObjectNode()
                    .put("status", "ok")
                    .put("message", "ApiGateway no configurado (modo dry-run)")
                    .putArray("documentos");
            return dryRunResponse;
        }

        String emisor = ChileanRut.sanitizeRut(config.getSiiRut());
        String url = getBaseApiUrl() + DOCUMENTOS_BHE_ENDPOINT + emisor + "/" + effectivePeriodo + "?pagina=" + effectivePagina;

        Map<String, Object> body = new HashMap<>();
        body.put("auth", buildAuthCredentials());

        try {
            String payloadJson = objectMapper.writeValueAsString(body);
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<String> entity = new HttpEntity<>(payloadJson, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            JsonNode result = objectMapper.readTree(response.getBody());
            siiListCache.put(cacheKey, new CachedSiiList(result));
            return result;
        } catch (Exception e) {
            log.error("[INVOICE-LIST-ERROR] Error listando BHEs para período {}: {}", effectivePeriodo, e.getMessage());
            throw new RuntimeException("Error consultando listado de BHEs: " + e.getMessage(), e);
        }
    }

/** true si el listado de boletas del SII para la clave está en caché y vigente. */
    public boolean hasFreshIssuedListCache(String cacheKey) {
        CachedSiiList cached = siiListCache.get(cacheKey);
        return cached != null && !cached.isExpired();
    }

    public JsonNode listIssuedInvoices(String periodo, int pagina) {
        return listIssuedInvoices(periodo, pagina, false);
    }

    /**
     * Descarga el archivo binario PDF oficial de la boleta desde el SII.
     * Valida que el archivo recibido sea realmente un PDF binario (%PDF).
     * Si no es válido o contiene un error HTML del SII, intenta resolver automáticamente
     * el código real consultando el registro de boletas del período en el SII.
     */
    @Transactional
    public byte[] getInvoicePdf(String codigo) {
        if (!config.isConfigured()) {
            throw new IllegalStateException("ApiGateway no está configurado");
        }
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("El código de boleta no puede estar vacío");
        }

        byte[] pdfBytes = fetchPdfBytesFromSii(codigo);
        if (isValidPdfBytes(pdfBytes)) {
            return pdfBytes;
        }

        // Si falló con el código provisto, intentar auto-recuperación buscando en la BD y listado del SII
        log.warn("[INVOICE-PDF] El código '{}' no devolvió un PDF binario válido. Intentando auto-recuperación...", codigo);
        byte[] recoveredBytes = tryAutoRecoverPdf(codigo);
        if (isValidPdfBytes(recoveredBytes)) {
            return recoveredBytes;
        }

        // Si vino HTML o texto con error desde el SII
        String responseText = pdfBytes != null ? new String(pdfBytes, java.nio.charset.StandardCharsets.UTF_8) : "Respuesta vacía";
        log.error("[INVOICE-PDF-ERROR] No se pudo obtener PDF para código {}. Contenido respuesta: {}", codigo, responseText);

        if (responseText.contains("No existe la boleta")) {
            throw new IllegalStateException("El SII no encontró la boleta de honorarios electrónica con el código " + codigo);
        }
        throw new IllegalStateException("El servidor del SII no retornó un archivo PDF válido");
    }

    private byte[] fetchPdfBytesFromSii(String codigo) {
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
            log.warn("[INVOICE-PDF-FETCH] Error llamando a endpoint PDF para código {}: {}", codigo, e.getMessage());
            return null;
        }
    }

    private boolean isValidPdfBytes(byte[] bytes) {
        return bytes != null && bytes.length >= 4 && bytes[0] == 0x25 && bytes[1] == 0x50 && bytes[2] == 0x44 && bytes[3] == 0x46; // %PDF
    }

    private byte[] tryAutoRecoverPdf(String rawCodigo) {
        try {
            // 1. Buscar en logs por siiCode o por invoiceNumber (folio)
            Optional<InvoiceLog> logOpt = invoiceLogRepository.findBySiiCode(rawCodigo);
            if (logOpt.isEmpty()) {
                logOpt = invoiceLogRepository.findByInvoiceNumber(rawCodigo);
            }
            if (logOpt.isEmpty()) {
                List<InvoiceLog> allLogs = invoiceLogRepository.findAll();
                logOpt = allLogs.stream()
                        .filter(l -> rawCodigo.equalsIgnoreCase(l.getSiiCode()) || rawCodigo.equalsIgnoreCase(l.getSiiBarcode()))
                        .findFirst();
            }

            if (logOpt.isEmpty()) {
                return null;
            }

            InvoiceLog logEntry = logOpt.get();
            String folio = logEntry.getInvoiceNumber();
            if (folio == null || folio.isBlank() || folio.startsWith("MANUAL")) {
                return null;
            }

            // Determinar período de la boleta (YYYYMM) de forma segura sin lazy proxy exception
            LocalDate date = null;
            try {
                if (logEntry.getAppointment() != null) {
                    date = logEntry.getAppointment().getAppointmentDate();
                }
            } catch (Exception ignored) {}
            if (date == null) {
                date = logEntry.getCreatedAt() != null ? logEntry.getCreatedAt().toLocalDate() : LocalDate.now();
            }
            String periodo = date.format(DateTimeFormatter.ofPattern("yyyyMM"));

            // Consultar documentos del SII para ese período
            JsonNode siiDocs = listIssuedInvoices(periodo, 1, true);
            JsonNode docsArray = SiiResponseParser.extractDocsArray(siiDocs);

            if (docsArray == null || docsArray.isEmpty()) {
                // Fallback a período actual si difiere
                String currentPeriodo = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
                if (!currentPeriodo.equals(periodo)) {
                    siiDocs = listIssuedInvoices(currentPeriodo, 1, true);
                    docsArray = SiiResponseParser.extractDocsArray(siiDocs);
                }
            }

            if (docsArray != null) {
                for (JsonNode doc : docsArray) {
                    String docFolio = SiiResponseParser.extractFolio(doc);
                    boolean matchesFolio = false;
                    if (docFolio != null && !docFolio.equals("N/A")) {
                        if (folio.trim().equalsIgnoreCase(docFolio.trim())) {
                            matchesFolio = true;
                        } else {
                            try {
                                if (Long.parseLong(folio.trim()) == Long.parseLong(docFolio.trim())) {
                                    matchesFolio = true;
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }

                    if (matchesFolio) {
                        String realCode = SiiResponseParser.extractCodigo(doc);
                        if (realCode == null || realCode.isBlank() || realCode.equalsIgnoreCase(rawCodigo)) {
                            if (doc.has("codigo") && !doc.get("codigo").isNull() && !doc.get("codigo").asText().isBlank()) {
                                realCode = doc.get("codigo").asText().trim();
                            } else if (doc.has("Codigo") && !doc.get("Codigo").isNull() && !doc.get("Codigo").asText().isBlank()) {
                                realCode = doc.get("Codigo").asText().trim();
                            } else if (doc.has("id") && !doc.get("id").isNull() && !doc.get("id").asText().isBlank()) {
                                realCode = doc.get("id").asText().trim();
                            } else if (doc.has("codigo_verificacion") && !doc.get("codigo_verificacion").isNull()) {
                                realCode = doc.get("codigo_verificacion").asText().trim();
                            }
                        }

                        if (realCode != null && !realCode.isBlank()) {
                            log.info("[INVOICE-PDF-RECOVER] Código real encontrado para folio {}: {} (anterior: {}). Actualizando DB y reintentando...",
                                    folio, realCode, rawCodigo);
                            logEntry.setSiiCode(realCode);
                            invoiceLogRepository.save(logEntry);

                            byte[] recoveredBytes = fetchPdfBytesFromSii(realCode);
                            if (isValidPdfBytes(recoveredBytes)) {
                                return recoveredBytes;
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[INVOICE-PDF-RECOVER-WARN] Error en auto-recuperación de PDF para código {}: {}", rawCodigo, ex.getMessage());
        }
        return null;
    }

    /**
     * Anula una boleta de honorarios emitida previamente en el SII
     */
    public JsonNode cancelInvoice(Long folio, String causa) {
        if (!config.isConfigured()) {
            throw new IllegalStateException("ApiGateway no está configurado");
        }

        String emisor = ChileanRut.sanitizeRut(config.getSiiRut());
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

    // =========================================================================
    // PAYLOAD BUILDERS Y HELPERS
    // =========================================================================

    private Map<String, Object> buildEmitirPayload(Appointment appointment, Customer customer, BigDecimal amount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("auth", buildAuthCredentials());

        Map<String, Object> boleta = new HashMap<>();

        Map<String, Object> encabezado = new HashMap<>();
        Map<String, Object> idDoc = new HashMap<>();
        
        LocalDate serviceDate = (appointment != null && appointment.getAppointmentDate() != null)
                ? appointment.getAppointmentDate()
                : LocalDate.now();
        idDoc.put("FchEmis", serviceDate.format(DATE_FORMATTER));
        idDoc.put("TipoRetencion", 2); // 2: Retención la hace el emisor (contribuyente de 2da categoría)
        encabezado.put("IdDoc", idDoc);

        Map<String, Object> emisor = new HashMap<>();
        emisor.put("RUTEmisor", ChileanRut.sanitizeRut(config.getSiiRut()));
        encabezado.put("Emisor", emisor);

        Map<String, Object> receptor = new HashMap<>();
        receptor.put("RUTRecep", ChileanRut.sanitizeRut(customer.getRut()));
        receptor.put("RznSocRecep", customer.getFullName() != null ? customer.getFullName() : "Cliente BunnyCure");
        receptor.put("DirRecep", "San Felipe");
        receptor.put("CmnaRecep", "Valparaiso");
        encabezado.put("Receptor", receptor);

        boleta.put("Encabezado", encabezado);

        Map<String, Object> detalleItem = new HashMap<>();
        detalleItem.put("NmbItem", "Servicios de Manicura");
        detalleItem.put("MontoItem", amount != null ? amount.longValue() : 0L);

        boleta.put("Detalle", List.of(detalleItem));

        payload.put("boleta", boleta);
        return payload;
    }

    private Map<String, Object> buildAuthCredentials() {
        Long currentTenantId = cl.bunnycure.config.TenantContext.getCurrentTenantId();
        String siiRut = config.getSiiRut();
        String siiPassword = config.getSiiPassword();

        if (tenantRepository != null && currentTenantId != null) {
            Optional<cl.bunnycure.domain.model.Tenant> tenantOpt = tenantRepository.findById(currentTenantId);
            if (tenantOpt.isPresent()) {
                cl.bunnycure.domain.model.Tenant t = tenantOpt.get();
                if (t.getSiiRut() != null && !t.getSiiRut().isBlank() && t.getSiiPassword() != null && !t.getSiiPassword().isBlank()) {
                    siiRut = t.getSiiRut();
                    siiPassword = t.getSiiPassword();
                    log.debug("[SII-AUTH] Utilizando credenciales específicas del salón '{}' (ID={}) para emisión BHE", t.getName(), t.getId());
                }
            }
        }

        Map<String, Object> auth = new HashMap<>();
        Map<String, String> pass = new HashMap<>();
        pass.put("rut", ChileanRut.sanitizeRut(siiRut));
        pass.put("clave", siiPassword != null ? siiPassword : "");
        auth.put("pass", pass);
        return auth;
    }

    private HttpHeaders buildAuthHeaders() {
        Long currentTenantId = cl.bunnycure.config.TenantContext.getCurrentTenantId();
        String token = config.getToken();

        if (tenantRepository != null && currentTenantId != null) {
            Optional<cl.bunnycure.domain.model.Tenant> tenantOpt = tenantRepository.findById(currentTenantId);
            if (tenantOpt.isPresent()) {
                cl.bunnycure.domain.model.Tenant t = tenantOpt.get();
                if (t.getSiiToken() != null && !t.getSiiToken().isBlank()) {
                    token = t.getSiiToken();
                }
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (token != null && !token.isBlank()) {
            token = token.trim();
            if (token.startsWith("Token ") || token.startsWith("Bearer ")) {
                headers.set("Authorization", token);
            } else {
                headers.set("Authorization", "Token " + token);
            }
        }
        return headers;
    }

    // =========================================================================
    // RUT VALIDATION & SANITIZATION
    // =========================================================================

    // =========================================================================
    // LOG PERSISTENCE
    // =========================================================================

    @Transactional
    public InvoiceLog createSuccessLog(Appointment appointment, Customer customer, BigDecimal amount,
            String invoiceNumber, String siiCode, String siiBarcode) {
        InvoiceLog logEntry = invoiceLogRepository.findByAppointmentId(appointment.getId())
                .orElseGet(() -> InvoiceLog.builder()
                        .appointment(appointment)
                        .customer(customer)
                        .build());

        logEntry.setCustomer(customer);
        logEntry.setAmountInClp(amount);
        logEntry.setInvoiceNumber(invoiceNumber);
        logEntry.setSiiCode(siiCode);
        logEntry.setSiiBarcode(siiBarcode);
        logEntry.setDescription("Boleta de honorarios SII - Servicios de estética BunnyCure");
        logEntry.setStatus("SUCCESS");
        logEntry.setErrorMessage(null);
        return invoiceLogRepository.save(logEntry);
    }

    @Transactional
    public void createFailedLog(Appointment appointment, Customer customer, BigDecimal amount, String errorMessage) {
        InvoiceLog logEntry = invoiceLogRepository.findByAppointmentId(appointment.getId())
                .orElseGet(() -> InvoiceLog.builder()
                        .appointment(appointment)
                        .customer(customer)
                        .build());

        logEntry.setCustomer(customer);
        logEntry.setAmountInClp(amount);
        logEntry.setDescription("Boleta de honorarios SII - Servicios de estética BunnyCure");
        logEntry.setStatus("FAILED");
        logEntry.setErrorMessage(errorMessage);
        invoiceLogRepository.save(logEntry);
    }
}
