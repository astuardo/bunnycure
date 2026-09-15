package cl.bunnycure.web.controller;

import cl.bunnycure.service.marketing.MarketingCampaignService;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.marketing.AudiencePreviewDto;
import cl.bunnycure.web.dto.marketing.AudienceType;
import cl.bunnycure.web.dto.marketing.CampaignDispatchRequestDto;
import cl.bunnycure.web.dto.marketing.CampaignDispatchResultDto;
import cl.bunnycure.web.dto.marketing.MarketingTemplateDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "Marketing & Campañas", description = "API para gestión y envío de campañas de marketing estacionales por WhatsApp")
@RestController
@RequestMapping("/api/marketing")
@RequiredArgsConstructor
public class MarketingApiController {

    private final MarketingCampaignService campaignService;
    private final cl.bunnycure.service.marketing.MarketingTemplateAiService templateAiService;

    @Operation(summary = "Crear y registrar plantilla de marketing mediante Agente IA")
    @PostMapping("/templates/ai-generate")
    public ResponseEntity<ApiResponse<MarketingTemplateDto>> generateAiTemplate(
            @Valid @RequestBody cl.bunnycure.web.dto.marketing.AiTemplateGenerateRequestDto request) {
        log.info("[API-MARKETING] Solicitud de creación de plantilla con IA: prompt='{}'", request.getPrompt());
        MarketingTemplateDto template = templateAiService.generateAndSaveTemplate(
                request.getPrompt(),
                request.isAutoRegisterInMeta(),
                "WEB_UI"
        );
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @Operation(summary = "Obtener catálogo de plantillas de marketing con estado en Meta")
    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<List<MarketingTemplateDto>>> getTemplates() {
        List<MarketingTemplateDto> templates = campaignService.getAvailableTemplates();
        return ResponseEntity.ok(ApiResponse.success(templates));
    }

    @Operation(summary = "Sincronizar y registrar plantillas en Meta Graph API")
    @PostMapping("/templates/sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncTemplates() {
        log.info("[API-MARKETING] Solicitud de sincronización de plantillas en Meta");
        Map<String, Object> result = campaignService.syncTemplatesWithMeta();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Previsualizar tamaño y muestra de audiencia")
    @GetMapping("/audience-preview")
    public ResponseEntity<ApiResponse<AudiencePreviewDto>> previewAudience(
            @RequestParam(defaultValue = "ALL") AudienceType audienceType,
            @RequestParam(required = false) List<Long> customerIds) {
        AudiencePreviewDto preview = campaignService.previewAudience(audienceType, customerIds);
        return ResponseEntity.ok(ApiResponse.success(preview));
    }

    @Operation(summary = "Despachar campaña de marketing por WhatsApp")
    @PostMapping("/campaigns/dispatch")
    public ResponseEntity<ApiResponse<CampaignDispatchResultDto>> dispatchCampaign(
            @Valid @RequestBody CampaignDispatchRequestDto request) {
        log.info("[API-MARKETING] Despachando campaña '{}' a audiencia '{}' (test: {})",
                request.getTemplateName(), request.getAudienceType(), request.getTestPhoneNumber());

        CampaignDispatchResultDto result = campaignService.dispatchCampaign(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Actualizar el contenido de una plantilla en Meta Graph API")
    @PutMapping("/templates/{name}")
    public ResponseEntity<ApiResponse<MarketingTemplateDto>> updateTemplate(
            @PathVariable String name,
            @Valid @RequestBody cl.bunnycure.web.dto.marketing.TemplateUpdateRequestDto request) {
        log.info("[API-MARKETING] Solicitud de actualización para plantilla '{}'", name);
        return campaignService.updateTemplate(name, request)
                .map(t -> ResponseEntity.ok(ApiResponse.success(t)))
                .orElseGet(() -> ResponseEntity.badRequest().body(ApiResponse.error("No se pudo actualizar la plantilla en Meta", "UPDATE_FAILED")));
    }
}
