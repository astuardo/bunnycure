package cl.bunnycure.service;

import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.CustomerServiceRecord;
import cl.bunnycure.domain.repository.CustomerServiceRecordRepository;
import cl.bunnycure.web.dto.WhatsAppWebhookDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CustomerServiceRecordService {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceRecordService.class);
    private static final int MAX_RECORDS_PER_CUSTOMER = 3;
    private static final Pattern CLIENT_PHONE_PATTERN = Pattern.compile("(?im)^\\s*cliente\\s*:\\s*(.+)$");
    private static final Pattern SERVICE_DETAIL_PATTERN = Pattern.compile("(?im)^\\s*servicio\\s*:\\s*(.+)$");

    private final CustomerServiceRecordRepository customerServiceRecordRepository;
    private final CustomerService customerService;
    private final WhatsAppService whatsAppService;

    public CustomerServiceRecordService(CustomerServiceRecordRepository customerServiceRecordRepository,
                                        CustomerService customerService,
                                        WhatsAppService whatsAppService) {
        this.customerServiceRecordRepository = customerServiceRecordRepository;
        this.customerService = customerService;
        this.whatsAppService = whatsAppService;
    }

    @Transactional(readOnly = true)
    public List<CustomerServiceRecord> findLatestByCustomerId(Long customerId) {
        return customerServiceRecordRepository.findTop3ByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public Optional<CustomerServiceRecord> findById(Long id) {
        return customerServiceRecordRepository.findById(id);
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
        record.setPhotoCaption(trimToNull(message.getImage().getCaption()));

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
            return ParsedCaption.invalid();
        }

        Matcher clientMatcher = CLIENT_PHONE_PATTERN.matcher(raw);
        Matcher serviceMatcher = SERVICE_DETAIL_PATTERN.matcher(raw);
        if (!clientMatcher.find() || !serviceMatcher.find()) {
            return ParsedCaption.invalid();
        }

        String customerPhone = trimToNull(clientMatcher.group(1));
        String serviceDetail = trimToNull(serviceMatcher.group(1));
        if (customerPhone == null || serviceDetail == null) {
            return ParsedCaption.invalid();
        }

        return new ParsedCaption(customerPhone, serviceDetail);
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
