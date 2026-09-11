package cl.bunnycure.service.marketing;

import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.service.NotificationLogService;
import cl.bunnycure.service.WhatsAppService;
import cl.bunnycure.web.dto.marketing.AudiencePreviewDto;
import cl.bunnycure.web.dto.marketing.AudienceType;
import cl.bunnycure.web.dto.marketing.CampaignDispatchRequestDto;
import cl.bunnycure.web.dto.marketing.CampaignDispatchResultDto;
import cl.bunnycure.web.dto.marketing.MarketingTemplateDto;
import cl.bunnycure.web.dto.marketing.TemplateUpdateRequestDto;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingCampaignService {

    private final CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;
    private final WhatsAppService whatsAppService;
    private final NotificationLogService notificationLogService;
    private final MarketingTemplateCatalog templateCatalog;

    /**
     * Obtiene el catálogo de plantillas combinando la definición local con su estado real en Meta.
     */
    public List<MarketingTemplateDto> getAvailableTemplates() {
        Map<String, MetaTemplateInfo> metaTemplates = fetchMetaTemplatesIndex();

        return templateCatalog.getAllDefinitions().stream()
                .map(def -> {
                    MetaTemplateInfo info = metaTemplates.get(def.name().toLowerCase());
                    String status = info != null ? info.status() : "NOT_REGISTERED";
                    String metaId = info != null ? info.id() : null;
                    return def.toDto(status, metaId);
                })
                .collect(Collectors.toList());
    }

    /**
     * Sincroniza y crea en Meta Graph API las plantillas del catálogo que aún no estén registradas.
     */
    public Map<String, Object> syncTemplatesWithMeta() {
        Map<String, MetaTemplateInfo> metaTemplates = fetchMetaTemplatesIndex();
        List<String> created = new ArrayList<>();
        List<String> alreadyExisted = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (MarketingTemplateCatalog.TemplateDefinition def : templateCatalog.getAllDefinitions()) {
            if (metaTemplates.containsKey(def.name().toLowerCase())) {
                alreadyExisted.add(def.name());
            } else {
                log.info("[MARKETING-SYNC] Registrando plantilla en Meta: {}", def.name());
                Optional<JsonNode> result = whatsAppService.createMessageTemplate(def.toMetaPayload());
                if (result.isPresent()) {
                    created.add(def.name());
                } else {
                    failed.add(def.name());
                }
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("created", created);
        response.put("alreadyExisted", alreadyExisted);
        response.put("failed", failed);
        response.put("totalCatalog", templateCatalog.getAllDefinitions().size());
        return response;
    }

    /**
     * Previsualiza la audiencia según el criterio de segmentación.
     */
    public AudiencePreviewDto previewAudience(AudienceType audienceType) {
        List<CustomerWithLastVisit> eligible = getEligibleCustomersWithVisit(audienceType);

        List<AudiencePreviewDto.SampleRecipientDto> samples = eligible.stream()
                .limit(10)
                .map(c -> AudiencePreviewDto.SampleRecipientDto.builder()
                        .id(c.customer().getId())
                        .fullName(c.customer().getFullName())
                        .maskedPhone(maskPhone(c.customer().getPhone()))
                        .lastVisitDate(c.lastVisit() != null ? c.lastVisit().toString() : "Sin visitas registradas")
                        .completedVisits(c.customer().getTotalCompletedVisits() != null ? c.customer().getTotalCompletedVisits() : 0)
                        .build())
                .collect(Collectors.toList());

        return AudiencePreviewDto.builder()
                .audienceType(audienceType)
                .audienceDescription(audienceType.getDescription())
                .totalCount(eligible.size())
                .sampleRecipients(samples)
                .build();
    }

    /**
     * Despacha la campaña de marketing por WhatsApp de forma controlada.
     */
    public CampaignDispatchResultDto dispatchCampaign(CampaignDispatchRequestDto request) {
        MarketingTemplateCatalog.TemplateDefinition template = templateCatalog.findByName(request.getTemplateName())
                .orElseThrow(() -> new IllegalArgumentException("Plantilla no encontrada: " + request.getTemplateName()));

        // Modo prueba si se especifica un número específico
        if (request.getTestPhoneNumber() != null && !request.getTestPhoneNumber().isBlank()) {
            return dispatchTestMessage(template, request.getTestPhoneNumber().trim(), request.getCustomBenefit(), request.getCustomParameters());
        }

        List<CustomerWithLastVisit> targets = getEligibleCustomersWithVisit(request.getAudienceType());
        log.info("[MARKETING-CAMPAIGN] Iniciando despacho masivo de '{}' a {} clientas (Audiencia: {})",
                template.name(), targets.size(), request.getAudienceType());

        int sent = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < targets.size(); i++) {
            Customer customer = targets.get(i).customer();
            try {
                String firstName = customer.getFullName().trim().split("\\s+")[0];
                List<String> bodyParams = buildBodyParams(template, firstName, request.getCustomBenefit(), request.getCustomParameters());

                boolean ok = whatsAppService.sendTemplateSync(
                        customer.getPhone(),
                        template.name(),
                        template.language(),
                        null,
                        bodyParams,
                        null
                );

                if (ok) {
                    sent++;
                    notificationLogService.logMarketingWhatsApp(
                            customer,
                            customer.getPhone(),
                            template.name(),
                            "Campaña: " + template.displayName(),
                            null
                    );
                } else {
                    failed++;
                    if (errors.size() < 10) {
                        errors.add("Fallo al enviar a " + maskPhone(customer.getPhone()));
                    }
                }
            } catch (Exception ex) {
                failed++;
                log.error("[MARKETING-ERROR] Error al enviar a {}: {}", customer.getPhone(), ex.getMessage());
                if (errors.size() < 10) {
                    errors.add(customer.getFullName() + ": " + ex.getMessage());
                }
            }

            // Rate limit: pausa de 500ms cada 10 envíos para no sobrecargar CPU ni sobrepasar cuotas de Meta
            if ((i + 1) % 10 == 0 && i < targets.size() - 1) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        log.info("[MARKETING-CAMPAIGN] Campaña '{}' finalizada. Enviados: {}, Fallidos: {}",
                template.name(), sent, failed);

        return CampaignDispatchResultDto.builder()
                .templateName(template.name())
                .audienceType(request.getAudienceType())
                .totalTargeted(targets.size())
                .sentCount(sent)
                .failedCount(failed)
                .errorMessages(errors)
                .isTestRun(false)
                .build();
    }

    private CampaignDispatchResultDto dispatchTestMessage(MarketingTemplateCatalog.TemplateDefinition template, String phone, String customBenefit, List<String> customParams) {
        log.info("[MARKETING-TEST] Enviando mensaje de prueba de '{}' a {}", template.name(), phone);

        List<String> bodyParams = buildBodyParams(template, "Prueba Admin", customBenefit, customParams);
        boolean ok = whatsAppService.sendTemplateSync(
                phone,
                template.name(),
                template.language(),
                null,
                bodyParams,
                null
        );

        List<String> errors = new ArrayList<>();
        if (!ok) {
            errors.add("No se pudo entregar el mensaje de prueba. Verifica que la plantilla esté APROBADA en Meta.");
        } else {
            notificationLogService.logMarketingWhatsApp(null, phone, template.name(), "[TEST] " + template.displayName(), null);
        }

        return CampaignDispatchResultDto.builder()
                .templateName(template.name())
                .audienceType(AudienceType.ALL)
                .totalTargeted(1)
                .sentCount(ok ? 1 : 0)
                .failedCount(ok ? 0 : 1)
                .errorMessages(errors)
                .isTestRun(true)
                .build();
    }

    private List<String> buildBodyParams(MarketingTemplateCatalog.TemplateDefinition template, String firstName, String customBenefit, List<String> customParams) {
        if (customParams != null && !customParams.isEmpty()) {
            return customParams;
        }
        if ("saludo_cumpleanos_bunnycure".equalsIgnoreCase(template.name())) {
            String benefit = (customBenefit != null && !customBenefit.isBlank())
                    ? customBenefit.trim()
                    : "un 15% de descuento exclusivo en tu próxima cita";
            return List.of(firstName, benefit);
        }
        if ("bunnycure_reactivacion_clienta".equalsIgnoreCase(template.name())) {
            String serviceName = (customBenefit != null && !customBenefit.isBlank())
                    ? customBenefit.trim()
                    : "Manicura Rusa / Permanente";
            return List.of(firstName, serviceName);
        }
        return List.of(firstName);
    }

    public Optional<MarketingTemplateDto> updateTemplate(String templateName, TemplateUpdateRequestDto request) {
        Optional<MarketingTemplateCatalog.TemplateDefinition> optDef = templateCatalog.findByName(templateName);
        if (optDef.isEmpty()) {
            throw new IllegalArgumentException("Plantilla no encontrada en catálogo: " + templateName);
        }
        Map<String, MetaTemplateInfo> metaIndex = fetchMetaTemplatesIndex();
        MetaTemplateInfo metaInfo = metaIndex.get(templateName.toLowerCase());
        if (metaInfo == null || metaInfo.id() == null || metaInfo.id().isBlank()) {
            throw new IllegalStateException("La plantilla '" + templateName + "' no está registrada en Meta todavía");
        }

        List<Map<String, Object>> components = new ArrayList<>();
        if (request.getHeaderText() != null && !request.getHeaderText().isBlank()) {
            components.add(Map.of(
                    "type", "HEADER",
                    "format", "TEXT",
                    "text", request.getHeaderText().trim()
            ));
        }

        Map<String, Object> bodyComp = new HashMap<>();
        bodyComp.put("type", "BODY");
        bodyComp.put("text", request.getBodyText().trim());
        if (optDef.get().sampleVariables() != null && !optDef.get().sampleVariables().isEmpty()) {
            bodyComp.put("example", Map.of("body_text", List.of(optDef.get().sampleVariables())));
        }
        components.add(bodyComp);

        if (request.getFooterText() != null && !request.getFooterText().isBlank()) {
            components.add(Map.of(
                    "type", "FOOTER",
                    "text", request.getFooterText().trim()
            ));
        }

        if (request.getButtonText() != null && !request.getButtonText().isBlank() && request.getButtonUrl() != null && !request.getButtonUrl().isBlank()) {
            components.add(Map.of(
                    "type", "BUTTONS",
                    "buttons", List.of(Map.of(
                            "type", "URL",
                            "text", request.getButtonText().trim(),
                            "url", request.getButtonUrl().trim()
                    ))
            ));
        }

        Optional<JsonNode> res = whatsAppService.updateMessageTemplate(metaInfo.id(), Map.of("components", components));
        if (res.isPresent()) {
            log.info("[MARKETING] ✅ Plantilla '{}' (ID: {}) enviada a actualización en Meta", templateName, metaInfo.id());
            return Optional.of(optDef.get().toDto("PENDING", metaInfo.id()));
        }
        return Optional.empty();
    }

    private List<CustomerWithLastVisit> getEligibleCustomersWithVisit(AudienceType audienceType) {
        List<Customer> allCustomers = customerRepository.findAll();
        Map<Long, LocalDate> lastVisitMap = fetchLastVisitsMap();

        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysAgo = today.minusDays(30);
        LocalDate sixtyDaysAgo = today.minusDays(60);
        LocalDate fortyFiveDaysAgo = today.minusDays(45);

        return allCustomers.stream()
                // Validar teléfono y consentimiento WhatsApp
                .filter(c -> c.getPhone() != null && !c.getPhone().isBlank())
                .filter(c -> c.getNotificationPreference() == null || c.getNotificationPreference().allowsWhatsApp())
                .map(c -> new CustomerWithLastVisit(c, lastVisitMap.get(c.getId())))
                .filter(c -> switch (audienceType) {
                    case ALL -> true;
                    case INACTIVE_30_DAYS -> c.lastVisit == null || c.lastVisit.isBefore(thirtyDaysAgo);
                    case INACTIVE_60_DAYS -> c.lastVisit == null || c.lastVisit.isBefore(sixtyDaysAgo);
                    case ACTIVE_RECENT -> c.lastVisit != null && !c.lastVisit.isBefore(fortyFiveDaysAgo);
                    case FREQUENT_VIP -> c.customer.getTotalCompletedVisits() != null && c.customer.getTotalCompletedVisits() >= 3;
                    case BIRTHDAYS_TODAY -> c.customer.getBirthDate() != null
                            && c.customer.getBirthDate().getMonthValue() == today.getMonthValue()
                            && c.customer.getBirthDate().getDayOfMonth() == today.getDayOfMonth();
                    case BIRTHDAYS_THIS_MONTH -> c.customer.getBirthDate() != null
                            && c.customer.getBirthDate().getMonthValue() == today.getMonthValue();
                })
                .collect(Collectors.toList());
    }

    private Map<Long, LocalDate> fetchLastVisitsMap() {
        Map<Long, LocalDate> map = new HashMap<>();
        try {
            List<Object[]> results = appointmentRepository.findLastCompletedAppointmentDatePerCustomer();
            for (Object[] row : results) {
                Long customerId = (Long) row[0];
                LocalDate date = (LocalDate) row[1];
                if (customerId != null && date != null) {
                    map.put(customerId, date);
                }
            }
        } catch (Exception ex) {
            log.warn("[MARKETING] No se pudo obtener mapa de últimas visitas: {}", ex.getMessage());
        }
        return map;
    }

    private Map<String, MetaTemplateInfo> fetchMetaTemplatesIndex() {
        Map<String, MetaTemplateInfo> index = new HashMap<>();
        try {
            Optional<JsonNode> metaResponse = whatsAppService.fetchMessageTemplates();
            if (metaResponse.isPresent()) {
                JsonNode data = metaResponse.get().path("data");
                if (data.isArray()) {
                    for (JsonNode t : data) {
                        String name = t.path("name").asText("").toLowerCase();
                        String status = t.path("status").asText("UNKNOWN");
                        String id = t.path("id").asText("");
                        if (!name.isBlank()) {
                            index.put(name, new MetaTemplateInfo(status, id));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.error("[MARKETING] Error al consultar índice de templates de Meta: {}", ex.getMessage());
        }
        return index;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) return phone;
        int len = phone.length();
        return phone.substring(0, len - 4).replaceAll("\\d", "*") + phone.substring(len - 4);
    }

    public record CustomerWithLastVisit(Customer customer, LocalDate lastVisit) {}
    public record MetaTemplateInfo(String status, String id) {}
}
