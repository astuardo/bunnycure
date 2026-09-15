package cl.bunnycure.service.marketing;

import cl.bunnycure.domain.model.MarketingTemplateEntity;
import cl.bunnycure.domain.repository.MarketingTemplateRepository;
import cl.bunnycure.service.WhatsAppService;
import cl.bunnycure.web.dto.marketing.MarketingTemplateDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketingTemplateAiServiceTest {

    @Mock
    private MarketingTemplateRepository templateRepository;

    @Mock
    private WhatsAppService whatsAppService;

    private MarketingTemplateCatalog templateCatalog;
    private MarketingTemplateAiService aiService;

    @BeforeEach
    void setUp() {
        templateCatalog = new MarketingTemplateCatalog(templateRepository);
        aiService = new MarketingTemplateAiService(
                templateRepository,
                whatsAppService,
                templateCatalog
        );
    }

    @Test
    void generateAndSaveTemplate_GeneratesValidMetaTemplateAndPersists() {
        String prompt = "Crea una plantilla para CyberDay con 20% de descuento en manicura rusa y acrílicas";

        when(templateRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
        when(templateRepository.save(any(MarketingTemplateEntity.class))).thenAnswer(inv -> {
            MarketingTemplateEntity e = inv.getArgument(0);
            e.setId(99L);
            return e;
        });

        MarketingTemplateDto result = aiService.generateAndSaveTemplate(prompt, false, "TEST");

        assertNotNull(result);
        assertTrue(result.getName().startsWith("promo_"));
        assertTrue(result.getName().matches("^[a-z0-9_]+$"));
        assertTrue(result.getBodyText().contains("{{1}}"));
        assertTrue(result.getBodyText().contains("20% de descuento"));
        assertEquals("MARKETING", result.getCategory());
        assertEquals("es_CL", result.getLanguage());
        assertNotNull(result.getButtonUrl());
        assertTrue(result.getButtonUrl().startsWith("https://"));

        ArgumentCaptor<MarketingTemplateEntity> captor = ArgumentCaptor.forClass(MarketingTemplateEntity.class);
        verify(templateRepository).save(captor.capture());
        MarketingTemplateEntity saved = captor.getValue();
        assertEquals("TEST", saved.getSource());
        assertEquals(result.getName(), saved.getName());
    }

    @Test
    void generateAndSaveTemplate_AutoRegistersInMeta() {
        String prompt = "Promoción de verano para clientas";

        when(templateRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
        when(templateRepository.save(any(MarketingTemplateEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        String metaJson = "{\"id\":\"meta-12345\",\"status\":\"PENDING\",\"category\":\"MARKETING\"}";
        try {
            var metaNode = new ObjectMapper().readTree(metaJson);
            when(whatsAppService.createMessageTemplate(anyMap())).thenReturn(Optional.of(metaNode));
        } catch (Exception e) {
            fail(e);
        }

        MarketingTemplateDto result = aiService.generateAndSaveTemplate(prompt, true, "WHATSAPP_ADMIN");

        assertNotNull(result);
        assertEquals("PENDING", result.getMetaStatus());
        assertEquals("meta-12345", result.getMetaId());
        verify(whatsAppService).createMessageTemplate(anyMap());
    }

    @Test
    void catalog_IncludesDatabaseTemplates() {
        MarketingTemplateEntity dbEntity = MarketingTemplateEntity.builder()
                .id(1L)
                .name("promo_black_friday_custom")
                .displayName("Black Friday Exclusivo 🖤")
                .occasion("Black Friday")
                .emoji("🖤")
                .category("MARKETING")
                .language("es_CL")
                .headerText("Black Friday BunnyCure")
                .bodyText("¡Hola {{1}}! 🖤 Descuentos únicos en tus uñas.")
                .footerText("BunnyCure Studio")
                .buttonText("Reservar")
                .buttonUrl("https://reservar.bunnycure.cl")
                .sampleVariables("Camila")
                .metaStatus("APPROVED")
                .metaId("meta-999")
                .build();

        when(templateRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(dbEntity));

        List<MarketingTemplateCatalog.TemplateDefinition> all = templateCatalog.getAllDefinitions();
        assertTrue(all.stream().anyMatch(t -> "promo_black_friday_custom".equals(t.name())));
    }
}
