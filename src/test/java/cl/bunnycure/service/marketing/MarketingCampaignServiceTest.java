package cl.bunnycure.service.marketing;

import cl.bunnycure.domain.enums.NotificationPreference;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketingCampaignServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private WhatsAppService whatsAppService;

    @Mock
    private NotificationLogService notificationLogService;

    private MarketingTemplateCatalog templateCatalog;
    private MarketingCampaignService campaignService;

    @BeforeEach
    void setUp() {
        templateCatalog = new MarketingTemplateCatalog();
        campaignService = new MarketingCampaignService(
                customerRepository,
                appointmentRepository,
                whatsAppService,
                notificationLogService,
                templateCatalog
        );
    }

    @Test
    void templateCatalog_HasRequiredSeasonalTemplates() {
        List<MarketingTemplateCatalog.TemplateDefinition> definitions = templateCatalog.getAllDefinitions();
        assertTrue(definitions.size() >= 7);

        assertNotNull(templateCatalog.findByName("promo_fiestas_patrias").orElse(null));
        assertNotNull(templateCatalog.findByName("promo_bienvenida_primavera").orElse(null));
        assertNotNull(templateCatalog.findByName("promo_dia_de_la_madre").orElse(null));
        assertNotNull(templateCatalog.findByName("promo_halloween_bunnycure").orElse(null));
        assertNotNull(templateCatalog.findByName("promo_navidad_bunnycure").orElse(null));
        assertNotNull(templateCatalog.findByName("promo_ano_nuevo_bunnycure").orElse(null));
        assertNotNull(templateCatalog.findByName("promo_san_valentin").orElse(null));
        assertNotNull(templateCatalog.findByName("bunnycure_reactivacion_clienta").orElse(null));
    }

    @Test
    void templateCatalog_BuildsValidMetaPayload() {
        MarketingTemplateCatalog.TemplateDefinition def = templateCatalog.findByName("promo_fiestas_patrias").orElseThrow();
        Map<String, Object> payload = def.toMetaPayload();

        assertEquals("promo_fiestas_patrias", payload.get("name"));
        assertEquals("MARKETING", payload.get("category"));
        assertEquals("es_CL", payload.get("language"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) payload.get("components");
        assertNotNull(components);
        assertTrue(components.stream().anyMatch(c -> "HEADER".equals(c.get("type"))));
        assertTrue(components.stream().anyMatch(c -> "BODY".equals(c.get("type"))));
        assertTrue(components.stream().anyMatch(c -> "BUTTONS".equals(c.get("type"))));
    }

    @Test
    void getAvailableTemplates_MapsMetaStatusCorrectly() throws Exception {
        String json = """
                {
                    "data": [
                        { "name": "promo_fiestas_patrias", "status": "APPROVED", "id": "111" },
                        { "name": "bunnycure_reactivacion_clienta", "status": "APPROVED", "id": "222" }
                    ]
                }
                """;
        var root = new ObjectMapper().readTree(json);
        when(whatsAppService.fetchMessageTemplates()).thenReturn(Optional.of(root));

        List<MarketingTemplateDto> dtos = campaignService.getAvailableTemplates();
        assertFalse(dtos.isEmpty());

        MarketingTemplateDto fiestas = dtos.stream()
                .filter(t -> "promo_fiestas_patrias".equals(t.getName()))
                .findFirst().orElseThrow();
        assertEquals("APPROVED", fiestas.getMetaStatus());
        assertEquals("111", fiestas.getMetaId());

        MarketingTemplateDto primavera = dtos.stream()
                .filter(t -> "promo_bienvenida_primavera".equals(t.getName()))
                .findFirst().orElseThrow();
        assertEquals("NOT_REGISTERED", primavera.getMetaStatus());
    }

    @Test
    void previewAudience_FiltersCorrectly() {
        Customer c1 = createCustomer(1L, "Camila Silva", "+56911111111", 5);
        Customer c2 = createCustomer(2L, "Javiera Pérez", "+56922222222", 1);
        Customer c3 = createCustomer(3L, "Francisca Tapia", null, 0); // No phone

        when(customerRepository.findAll()).thenReturn(List.of(c1, c2, c3));

        // c1 visitó hace 10 días, c2 visitó hace 90 días
        List<Object[]> lastVisits = List.of(
                new Object[]{ 1L, LocalDate.now().minusDays(10) },
                new Object[]{ 2L, LocalDate.now().minusDays(90) }
        );
        when(appointmentRepository.findLastCompletedAppointmentDatePerCustomer()).thenReturn(lastVisits);

        // ALL
        AudiencePreviewDto allPreview = campaignService.previewAudience(AudienceType.ALL);
        assertEquals(2, allPreview.getTotalCount()); // c1, c2 (c3 excluded due to no phone)

        // VIP
        AudiencePreviewDto vipPreview = campaignService.previewAudience(AudienceType.FREQUENT_VIP);
        assertEquals(1, vipPreview.getTotalCount()); // only c1 has >= 3 visits

        // INACTIVE_60_DAYS
        AudiencePreviewDto inactivePreview = campaignService.previewAudience(AudienceType.INACTIVE_60_DAYS);
        assertEquals(1, inactivePreview.getTotalCount()); // only c2 (> 60 days)
    }

    @Test
    void previewAudience_Birthdays() {
        LocalDate today = LocalDate.now();
        Customer c1 = createCustomer(1L, "Cumpleañera Hoy", "+56911111111", 2);
        c1.setBirthDate(LocalDate.of(1995, today.getMonthValue(), today.getDayOfMonth()));

        Customer c2 = createCustomer(2L, "Cumpleañera Este Mes", "+56922222222", 1);
        int otherDay = today.getDayOfMonth() == 1 ? 28 : 1;
        c2.setBirthDate(LocalDate.of(1992, today.getMonthValue(), otherDay));

        Customer c3 = createCustomer(3L, "Cumpleañera Otro Mes", "+56933333333", 4);
        int otherMonth = today.getMonthValue() == 12 ? 1 : today.getMonthValue() + 1;
        c3.setBirthDate(LocalDate.of(1990, otherMonth, 15));

        when(customerRepository.findAll()).thenReturn(List.of(c1, c2, c3));
        when(appointmentRepository.findLastCompletedAppointmentDatePerCustomer()).thenReturn(List.of());

        AudiencePreviewDto todayPreview = campaignService.previewAudience(AudienceType.BIRTHDAYS_TODAY);
        assertEquals(1, todayPreview.getTotalCount());
        assertEquals("Cumpleañera Hoy", todayPreview.getSampleRecipients().get(0).getFullName());

        AudiencePreviewDto monthPreview = campaignService.previewAudience(AudienceType.BIRTHDAYS_THIS_MONTH);
        assertEquals(2, monthPreview.getTotalCount()); // c1 + c2
    }

    @Test
    void dispatchCampaign_WithCustomBenefit() {
        when(whatsAppService.sendTemplateSync(
                anyString(),
                eq("saludo_cumpleanos_bunnycure"),
                eq("es_CL"),
                isNull(),
                anyList(),
                isNull()
        )).thenReturn(true);

        CampaignDispatchRequestDto request = CampaignDispatchRequestDto.builder()
                .templateName("saludo_cumpleanos_bunnycure")
                .audienceType(AudienceType.ALL)
                .testPhoneNumber("+56983692046")
                .customBenefit("un 20% de descuento especial y un exfoliante")
                .build();

        CampaignDispatchResultDto result = campaignService.dispatchCampaign(request);
        assertTrue(result.isTestRun());
        assertEquals(1, result.getSentCount());

        verify(whatsAppService).sendTemplateSync(
                eq("+56983692046"),
                eq("saludo_cumpleanos_bunnycure"),
                eq("es_CL"),
                isNull(),
                eq(List.of("Prueba Admin", "un 20% de descuento especial y un exfoliante")),
                isNull()
        );
    }

    @Test
    void dispatchCampaign_TestRun() {
        when(whatsAppService.sendTemplateSync(
                anyString(),
                eq("promo_fiestas_patrias"),
                eq("es_CL"),
                isNull(),
                anyList(),
                isNull()
        )).thenReturn(true);

        CampaignDispatchRequestDto request = CampaignDispatchRequestDto.builder()
                .templateName("promo_fiestas_patrias")
                .audienceType(AudienceType.ALL)
                .testPhoneNumber("+56983692046")
                .build();

        CampaignDispatchResultDto result = campaignService.dispatchCampaign(request);
        assertTrue(result.isTestRun());
        assertEquals(1, result.getSentCount());
        assertEquals(0, result.getFailedCount());

        verify(whatsAppService).sendTemplateSync(
                eq("+56983692046"),
                eq("promo_fiestas_patrias"),
                eq("es_CL"),
                isNull(),
                anyList(),
                isNull()
        );
        verify(notificationLogService).logMarketingWhatsApp(isNull(), eq("+56983692046"), eq("promo_fiestas_patrias"), anyString(), isNull());
    }

    @Test
    void previewAudience_SpecificCustomers() {
        Customer c1 = createCustomer(1L, "Camila Silva", "+56911111111", 5);
        Customer c2 = createCustomer(2L, "Javiera Pérez", "+56922222222", 1);
        Customer c3 = createCustomer(3L, "Francisca Tapia", null, 0); // No phone

        when(customerRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(c1, c2, c3));
        when(appointmentRepository.findLastCompletedAppointmentDatePerCustomer()).thenReturn(List.of());

        // Clientes específicos: de los 3 IDs, solo c1 y c2 son elegibles (c3 no tiene teléfono)
        AudiencePreviewDto preview = campaignService.previewAudience(AudienceType.SPECIFIC_CUSTOMERS, List.of(1L, 2L, 3L));
        assertEquals(2, preview.getTotalCount());
        assertEquals(2, preview.getSampleRecipients().size());

        // Si la lista está vacía
        AudiencePreviewDto emptyPreview = campaignService.previewAudience(AudienceType.SPECIFIC_CUSTOMERS, List.of());
        assertEquals(0, emptyPreview.getTotalCount());
    }

    @Test
    void dispatchCampaign_SpecificCustomers() {
        Customer c1 = createCustomer(10L, "Valentina Gomez", "+56944444444", 2);
        when(customerRepository.findAllById(List.of(10L))).thenReturn(List.of(c1));
        when(appointmentRepository.findLastCompletedAppointmentDatePerCustomer()).thenReturn(List.of());

        when(whatsAppService.sendTemplateSync(
                eq("+56944444444"),
                eq("promo_halloween_bunnycure"),
                eq("es_CL"),
                isNull(),
                anyList(),
                isNull()
        )).thenReturn(true);

        CampaignDispatchRequestDto request = CampaignDispatchRequestDto.builder()
                .templateName("promo_halloween_bunnycure")
                .audienceType(AudienceType.SPECIFIC_CUSTOMERS)
                .customerIds(List.of(10L))
                .build();

        CampaignDispatchResultDto result = campaignService.dispatchCampaign(request);
        assertFalse(result.isTestRun());
        assertEquals(1, result.getTotalTargeted());
        assertEquals(1, result.getSentCount());
        assertEquals(0, result.getFailedCount());

        verify(whatsAppService).sendTemplateSync(
                eq("+56944444444"),
                eq("promo_halloween_bunnycure"),
                eq("es_CL"),
                isNull(),
                eq(List.of("Valentina")),
                isNull()
        );
        verify(notificationLogService).logMarketingWhatsApp(eq(c1), eq("+56944444444"), eq("promo_halloween_bunnycure"), anyString(), isNull());
    }

    @Test
    void deleteTemplate_DeletesFromMetaAndDb() {
        when(whatsAppService.deleteMessageTemplate("promo_test")).thenReturn(true);

        boolean deleted = campaignService.deleteTemplate("promo_test");

        assertTrue(deleted);
        verify(whatsAppService).deleteMessageTemplate("promo_test");
    }

    private Customer createCustomer(Long id, String name, String phone, int completedVisits) {
        Customer c = new Customer();
        c.setId(id);
        c.setFullName(name);
        c.setPhone(phone);
        c.setTotalCompletedVisits(completedVisits);
        c.setNotificationPreference(NotificationPreference.BOTH);
        return c;
    }
}
