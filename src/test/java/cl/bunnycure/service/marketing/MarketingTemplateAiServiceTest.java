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

    @Test
    void generateAndSaveTemplate_FloresAmarillasPrompt() {
        String prompt = "Crea una campaña para el día en que se dan flores amarillas";

        when(templateRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
        when(templateRepository.save(any(MarketingTemplateEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        MarketingTemplateDto result = aiService.generateAndSaveTemplate(prompt, false, "WEB_UI");

        assertNotNull(result);
        assertEquals("promo_flores_amarillas", result.getName());
        assertTrue(result.getDisplayName().contains("Flores Amarillas"));
        assertTrue(result.getBodyText().contains("flores amarillas"));
        assertTrue(result.getBodyText().contains("{{1}}"));
        assertFalse(result.getHeaderText().contains("🎃"));
        assertFalse(result.getHeaderText().contains("🌼"));
    }

    @Test
    void generateAndSaveTemplate_DiaDeLaNoviaPrompt() {
        String prompt = "crea campaña para el dia de la novia";

        when(templateRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
        when(templateRepository.save(any(MarketingTemplateEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        MarketingTemplateDto result = aiService.generateAndSaveTemplate(prompt, false, "WEB_UI");

        assertNotNull(result);
        assertEquals("promo_dia_de_la_novia", result.getName());
        assertTrue(result.getDisplayName().contains("Día de la Novia"));
        assertTrue(result.getBodyText().contains("Día de la Novia"));
        assertTrue(result.getBodyText().contains("{{1}}"));
        assertFalse(result.getHeaderText().contains("💕"));
    }

    @Test
    void generateDraftPreview_ReturnsDraftWithoutSavingOrRegisteringInMeta() {
        String prompt = "Especial San Valentín 20% descuento";

        when(templateRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);

        MarketingTemplateDto draft = aiService.generateDraftPreview(prompt);

        assertNotNull(draft);
        assertEquals("DRAFT", draft.getMetaStatus());
        assertNull(draft.getMetaId());
        assertTrue(draft.getBodyText().contains("{{1}}"));
        assertTrue(draft.getBodyText().contains("20%"));
        // Asegurar que NO se guardó en BD ni se llamó a Meta
        verify(templateRepository, never()).save(any());
        verify(whatsAppService, never()).createMessageTemplate(anyMap());
    }

    @Test
    void saveApprovedTemplate_PersistsUserEditsAndRegistersInMeta() {
        cl.bunnycure.web.dto.marketing.SaveApprovedTemplateRequestDto request =
                cl.bunnycure.web.dto.marketing.SaveApprovedTemplateRequestDto.builder()
                        .name("promo_san_valentin_personalizada")
                        .displayName("San Valentín Editado ✨")
                        .occasion("San Valentín")
                        .emoji("💖")
                        .headerText("Especial Amor BunnyCure")
                        .bodyText("¡Hola {{1}}! Hemos ajustado este mensaje especialmente para ti.")
                        .footerText("BunnyCure Studio")
                        .buttonText("Pedir Cita")
                        .buttonUrl("https://reservar.bunnycure.cl/san-valentin")
                        .sampleVariables(List.of("Camila"))
                        .autoRegisterInMeta(true)
                        .build();

        when(templateRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
        when(templateRepository.save(any(MarketingTemplateEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        String metaJson = "{\"id\":\"meta-approved-888\",\"status\":\"PENDING\",\"category\":\"MARKETING\"}";
        try {
            var metaNode = new ObjectMapper().readTree(metaJson);
            when(whatsAppService.createMessageTemplate(anyMap())).thenReturn(Optional.of(metaNode));
        } catch (Exception e) {
            fail(e);
        }

        MarketingTemplateDto result = aiService.saveApprovedTemplate(request, "WEB_UI");

        assertNotNull(result);
        assertEquals("promo_san_valentin_personalizada", result.getName());
        assertEquals("San Valentín Editado ✨", result.getDisplayName());
        assertEquals("Especial Amor BunnyCure", result.getHeaderText());
        assertEquals("¡Hola {{1}}! Hemos ajustado este mensaje especialmente para ti.", result.getBodyText());
        assertEquals("Pedir Cita", result.getButtonText());
        assertEquals("https://reservar.bunnycure.cl/san-valentin", result.getButtonUrl());
        assertEquals("PENDING", result.getMetaStatus());
        assertEquals("meta-approved-888", result.getMetaId());

        verify(templateRepository).save(any(MarketingTemplateEntity.class));
        verify(whatsAppService).createMessageTemplate(anyMap());
    }
}
