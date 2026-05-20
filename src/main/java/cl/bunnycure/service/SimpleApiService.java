package cl.bunnycure.service;

import cl.bunnycure.config.SimpleApiConfig;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.InvoiceLog;
import cl.bunnycure.domain.repository.InvoiceLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SimpleApiService {

    private final SimpleApiConfig config;
    private final InvoiceLogRepository invoiceLogRepository;
    @Qualifier("simpleApiRestTemplate")
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String BOLETA_ENDPOINT = "/api/bhe/emitir";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MONTHLY_INVOICE_LIMIT = 30;

    /**
     * Generates an invoice (boleta de honorarios) for a completed appointment
     * @param appointment The completed appointment
     * @param customer The customer receiving the invoice
     * @param amount The amount in CLP
     * @return Optional with invoice number if successful
     */
    @Transactional
    public Optional<String> generateInvoice(Appointment appointment, Customer customer, BigDecimal amount) {
        // Check if already processed
        Optional<InvoiceLog> existingLog = invoiceLogRepository.findByAppointmentId(appointment.getId());
        if (existingLog.isPresent()) {
            log.warn("[INVOICE] Invoice already generated for appointment {}", appointment.getId());
            return Optional.ofNullable(existingLog.get().getInvoiceNumber());
        }

        if (!canGenerateMoreInvoicesThisMonth()) {
            String message = "Monthly invoice limit reached (" + MONTHLY_INVOICE_LIMIT + "/" + MONTHLY_INVOICE_LIMIT + ")";
            log.warn("[INVOICE-SKIP] {}", message);
            createFailedLog(appointment, customer, amount, message);
            return Optional.empty();
        }

        // Validate configuration
        if (!config.isConfigured()) {
            log.warn("[INVOICE-SKIP] SimpleAPI not configured. Set simple-api.enabled=true and provide API key.");
            createFailedLog(appointment, customer, amount, "SimpleAPI not configured");
            return Optional.empty();
        }

        // Validate RUT
        if (customer.getRut() == null || customer.getRut().isBlank()) {
            log.warn("[INVOICE-ERROR] Cannot generate invoice: customer {} has no RUT", customer.getId());
            createFailedLog(appointment, customer, amount, "Customer RUT is required");
            return Optional.empty();
        }

        try {
            String invoiceNumber = invoiceWithSimpleApi(customer, amount);
            createSuccessLog(appointment, customer, amount, invoiceNumber, null);
            log.info("[INVOICE] Successfully generated invoice {} for customer {}", invoiceNumber, customer.getId());
            return Optional.of(invoiceNumber);
        } catch (Exception e) {
            log.error("[INVOICE-ERROR] Failed to generate invoice for appointment {}: {}", appointment.getId(), e.getMessage(), e);
            createFailedLog(appointment, customer, amount, e.getMessage());
            return Optional.empty();
        }
    }

    public int getMonthlyInvoiceLimit() {
        return MONTHLY_INVOICE_LIMIT;
    }

    public long getGeneratedInvoicesThisMonth() {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate firstDayNextMonth = firstDay.plusMonths(1);
        LocalDateTime start = firstDay.atStartOfDay();
        LocalDateTime end = firstDayNextMonth.atStartOfDay();
        return invoiceLogRepository.countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan("SUCCESS", start, end);
    }

    public boolean canGenerateMoreInvoicesThisMonth() {
        return getGeneratedInvoicesThisMonth() < MONTHLY_INVOICE_LIMIT;
    }

    /**
     * Makes the actual API call to SimpleAPI
     */
    private String invoiceWithSimpleApi(Customer customer, BigDecimal amount) throws Exception {
        String url = config.getEndpoint() + BOLETA_ENDPOINT;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("rut", customer.getRut());
        requestBody.put("nombre", customer.getFullName());
        requestBody.put("email", customer.getEmail() != null ? customer.getEmail() : "");
        requestBody.put("montoBruto", amount);
        requestBody.put("descripcion", "Servicios de estética BunnyCure");
        requestBody.put("fechaEmision", LocalDate.now().format(DATE_FORMATTER));
        requestBody.put("moneda", "CLP");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", config.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());

                // Check for success
                if (jsonResponse.has("success") && jsonResponse.get("success").asBoolean()) {
                    String invoiceNumber = jsonResponse.has("invoiceNumber") 
                        ? jsonResponse.get("invoiceNumber").asText() 
                        : jsonResponse.has("folio") 
                            ? jsonResponse.get("folio").asText() 
                            : "N/A";
                    return invoiceNumber;
                }

                // Check error in response
                if (jsonResponse.has("error")) {
                    throw new RuntimeException("SimpleAPI Error: " + jsonResponse.get("error").asText());
                }
                
                throw new RuntimeException("Invalid response format from SimpleAPI");
            } else {
                throw new RuntimeException("SimpleAPI returned status " + response.getStatusCode());
            }
        } catch (RestClientException e) {
            throw new RuntimeException("Failed to call SimpleAPI: " + e.getMessage(), e);
        }
    }

    /**
     * Validates RUT format (Chilean ID number)
     * Format: XX.XXX.XXX-X
     */
    public boolean validateRutFormat(String rut) {
        if (rut == null || rut.isBlank()) {
            return false;
        }

        // Remove dots and spaces for validation
        String cleaned = rut.replaceAll("[.-]", "");

        // Must have at least 8 characters (digits) and 1 check digit
        if (cleaned.length() < 9) {
            return false;
        }

        // Check if format contains only digits and valid separators
        if (!rut.matches("^\\d{1,2}\\.\\d{3}\\.\\d{3}-[0-9K]$")) {
            return false;
        }

        return true;
    }

    /**
     * Calculates RUT check digit (optional validation)
     */
    public String calculateRutCheckDigit(String rut) {
        String cleaned = rut.replaceAll("[.-]", "");
        if (cleaned.length() < 8) {
            return null;
        }

        String number = cleaned.substring(0, cleaned.length() - 1);
        int multiplier = 2;
        int sum = 0;

        for (int i = number.length() - 1; i >= 0; i--) {
            sum += Integer.parseInt(String.valueOf(number.charAt(i))) * multiplier;
            multiplier++;
            if (multiplier > 7) {
                multiplier = 2;
            }
        }

        int remainder = sum % 11;
        int checkDigit = 11 - remainder;

        if (checkDigit == 11) {
            return "0";
        } else if (checkDigit == 10) {
            return "K";
        } else {
            return String.valueOf(checkDigit);
        }
    }

    @Transactional
    private void createSuccessLog(Appointment appointment, Customer customer, BigDecimal amount, 
                                   String invoiceNumber, String transactionId) {
        InvoiceLog log = InvoiceLog.builder()
            .appointment(appointment)
            .customer(customer)
            .amountInClp(amount)
            .invoiceNumber(invoiceNumber)
            .simpleApiTransactionId(transactionId)
            .description("Boleta de honorarios - Servicios de estética")
            .status("SUCCESS")
            .build();
        invoiceLogRepository.save(log);
    }

    @Transactional
    private void createFailedLog(Appointment appointment, Customer customer, BigDecimal amount, String errorMessage) {
        InvoiceLog log = InvoiceLog.builder()
            .appointment(appointment)
            .customer(customer)
            .amountInClp(amount)
            .description("Boleta de honorarios - Servicios de estética")
            .status("FAILED")
            .errorMessage(errorMessage)
            .build();
        invoiceLogRepository.save(log);
    }
}
