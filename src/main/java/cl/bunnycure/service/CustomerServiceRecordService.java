package cl.bunnycure.service;

import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.CustomerServiceRecord;
import cl.bunnycure.domain.repository.CustomerServiceRecordRepository;
import cl.bunnycure.web.dto.WhatsAppWebhookDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceRecordService {

    private static final int MAX_RECORDS_PER_CUSTOMER = 3;
    // Formato etiquetado: CLIENTE: xxx / SERVICIO: yyy
    private static final Pattern CLIENT_PHONE_PATTERN  = Pattern.compile("(?im)^\\s*cliente\\s*:\\s*(.+)$");
    private static final Pattern SERVICE_DETAIL_PATTERN = Pattern.compile("(?im)^\\s*servicio\\s*:\\s*(.+)$");
    // Formato simple: primera línea = teléfono, resto = descripción
    private static final Pattern SIMPLE_PHONE_PATTERN  = Pattern.compile("^[+\\d][\\d\\s\\-().]{5,24}$");

    private final CustomerServiceRecordRepository customerServiceRecordRepository;
    private final CustomerService customerService;
    private final WhatsAppService whatsAppService;

    @Transactional(readOnly = true)
    public List<CustomerServiceRecord> findLatestByCustomerId(Long customerId) {
        return customerServiceRecordRepository.findTop3ByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public Optional<CustomerServiceRecord> findById(Long id) {
        return customerServiceRecordRepository.findById(id);
    }

    @Transactional
    public boolean deleteByIdForCustomer(Long recordId, Long customerId) {
        return customerServiceRecordRepository.findById(recordId)
                .filter(r -> r.getCustomer() != null && customerId.equals(r.getCustomer().getId()))
                .map(r -> {
                    customerServiceRecordRepository.delete(r);
                    log.info("[RECORD] ✅ Registro de servicio eliminado. recordId={} customerId={}", recordId, customerId);
                    return true;
                })
                .orElseGet(() -> {
                    log.warn("[RECORD] ⚠️ No se encontró registro id={} para cliente id={}", recordId, customerId);
                    return false;
                });
    }

    @Transactional
    public Optional<CustomerServiceRecord> registerFromIncomingImage(WhatsAppWebhookDto.Message message) {
        if (message == null || message.getImage() == null) {
            return Optional.empty();
        }

        String messageId = trimToNull(message.getId());
        String mediaId = trimToNull(message.getImage().getId());
        if (messageId == null || mediaId == null) {
            log.warn("[WEBHOOK] Cannot persist customer record image without message/media id");
            return Optional.empty();
        }

        Optional<CustomerServiceRecord> existing = customerServiceRecordRepository.findBySourceMessageId(messageId);
        if (existing.isPresent()) {
            return existing;
        }

        ParsedCaption parsedCaption = parseCaption(message.getImage().getCaption());
        if (!parsedCaption.isValid()) {
            log.warn("[WEBHOOK] Caption does not match expected format CLIENTE/SERVICIO for message id={}", messageId);
            return Optional.empty();
        }

        Optional<Customer> customer = customerService.findByPhone(parsedCaption.customerPhone());
        if (customer.isEmpty()) {
            log.warn("[WEBHOOK] No customer found for parsed phone={} (message id={})", parsedCaption.customerPhone(), messageId);
            return Optional.empty();
        }

        CustomerServiceRecord record = new CustomerServiceRecord();
        record.setCustomer(customer.get());
        record.setSourceMessageId(messageId);
        record.setWhatsappMediaId(mediaId);
        record.setSourceFromPhone(normalizePhone(message.getFrom()));
        record.setClientPhoneInPayload(normalizePhone(parsedCaption.customerPhone()));
        record.setServiceDetail(parsedCaption.serviceDetail());
        // Keep only the service message in description, not the phone line from caption input.
        record.setPhotoCaption(parsedCaption.serviceDetail());

        whatsAppService.downloadImageByMediaId(mediaId).ifPresent(media -> {
            record.setPhotoData(media.content());
            record.setMimeType(trimToNull(media.mimeType()));
            record.setMediaSha256(trimToNull(media.sha256()));
        });

        CustomerServiceRecord saved = customerServiceRecordRepository.save(record);
        applyFifoLimit(saved.getCustomer().getId());
        return Optional.of(saved);
    }

    private void applyFifoLimit(Long customerId) {
        List<CustomerServiceRecord> ordered = customerServiceRecordRepository.findByCustomerIdOrderByCreatedAtAsc(customerId);
        if (ordered.size() <= MAX_RECORDS_PER_CUSTOMER) {
            return;
        }

        int overflow = ordered.size() - MAX_RECORDS_PER_CUSTOMER;
        for (int i = 0; i < overflow; i++) {
            customerServiceRecordRepository.delete(ordered.get(i));
        }
    }

    private ParsedCaption parseCaption(String caption) {
        String raw = trimToNull(caption);
        if (raw == null) {
            log.warn("[RECORD] Caption vacío o nulo — no se puede procesar");
            return ParsedCaption.invalid();
        }

        // Formato etiquetado: CLIENTE: xxx / SERVICIO: yyy
        Matcher clientMatcher  = CLIENT_PHONE_PATTERN.matcher(raw);
        Matcher serviceMatcher = SERVICE_DETAIL_PATTERN.matcher(raw);
        if (clientMatcher.find() && serviceMatcher.find()) {
            String phone  = trimToNull(clientMatcher.group(1));
            String detail = trimToNull(serviceMatcher.group(1));
            if (phone != null && detail != null) {
                log.info("[RECORD] Caption parseado en formato etiquetado. phone={}", phone);
                return new ParsedCaption(phone, detail);
            }
        }

        // Formato simple: primera línea = teléfono, segunda línea en adelante = descripción
        String[] lines = raw.split("\\r?\\n", 2);
        if (lines.length >= 2) {
            String firstLine  = lines[0].trim();
            String secondPart = lines[1].trim();
            if (!firstLine.isEmpty() && !secondPart.isEmpty()
                    && SIMPLE_PHONE_PATTERN.matcher(firstLine).matches()) {
                log.info("[RECORD] Caption parseado en formato simple. phone={}", firstLine);
                return new ParsedCaption(firstLine, secondPart);
            }
        }

        log.warn("[RECORD] Caption no coincide con ningún formato soportado. caption='{}'", raw);
        log.warn("[RECORD] Formatos válidos:");
        log.warn("[RECORD]   Simple  → primera línea: número de teléfono / segunda línea: descripción");
        log.warn("[RECORD]   Etiquetado → CLIENTE: +56XXXXXXXXX / SERVICIO: descripción");
        return ParsedCaption.invalid();
    }

    private String normalizePhone(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\D", "");
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record ParsedCaption(String customerPhone, String serviceDetail) {
        static ParsedCaption invalid() {
            return new ParsedCaption(null, null);
        }

        boolean isValid() {
            return customerPhone != null && serviceDetail != null;
        }
    }
}
