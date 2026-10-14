package cl.bunnycure.web.controller;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.repository.TenantRepository;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.TenantInfoDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Slf4j
@Tag(name = "Tenants", description = "API pública para resolución de salones y branding multi-tenant")
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class TenantPublicApiController {

    private final TenantRepository tenantRepository;
    private final cl.bunnycure.service.AppSettingsService appSettingsService;

    @Operation(
            summary = "Obtener información pública del salón (Branding)",
            description = "Resuelve y retorna los datos de marca, colores y contacto del salón según el dominio actual o slug."
    )
    @GetMapping({"/tenant-info", "/tenant-config"})
    public ResponseEntity<ApiResponse<TenantInfoDto>> getTenantInfo(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String slug
    ) {
        Optional<Tenant> tenantOpt = Optional.empty();

        if (domain != null && !domain.isBlank()) {
            tenantOpt = tenantRepository.findByCustomDomainIgnoreCase(domain.trim());
        }

        if (tenantOpt.isEmpty() && slug != null && !slug.isBlank()) {
            tenantOpt = tenantRepository.findBySlugIgnoreCase(slug.trim());
        }

        if (tenantOpt.isEmpty()) {
            Long currentTenantId = TenantContext.getCurrentTenantId();
            tenantOpt = tenantRepository.findById(currentTenantId);
        }

        Tenant tenant = tenantOpt.orElseGet(() -> tenantRepository.findById(1L).orElse(null));

        if (tenant == null) {
            return ResponseEntity.notFound().build();
        }

        String primaryColor = (tenant.getPrimaryColor() != null && !tenant.getPrimaryColor().isBlank())
                ? tenant.getPrimaryColor()
                : (appSettingsService != null ? appSettingsService.getAppPrimaryColor() : "#d48b70");

        String logoUrl = (tenant.getLogoUrl() != null && !tenant.getLogoUrl().isBlank())
                ? tenant.getLogoUrl()
                : (appSettingsService != null ? appSettingsService.getAppLogoUrl() : "/images/logo.png");

        String name = (tenant.getName() != null && !tenant.getName().isBlank())
                ? tenant.getName()
                : (appSettingsService != null ? appSettingsService.getAppName() : "BunnyCure");

        String configuredPhone = appSettingsService != null ? appSettingsService.getAppPhoneDisplay() : null;
        String phone;
        if (configuredPhone != null && !configuredPhone.isBlank()) {
            phone = configuredPhone;
        } else if (tenant.getPhone() != null && !tenant.getPhone().isBlank() && !"+56983692046".equals(tenant.getPhone().replaceAll("\\s+", ""))) {
            phone = tenant.getPhone();
        } else {
            phone = "+56 9 6449 9995";
        }

        String email = (tenant.getEmail() != null && !tenant.getEmail().isBlank())
                ? tenant.getEmail()
                : (appSettingsService != null ? appSettingsService.getAppEmail() : "contacto@bunnycure.cl");

        String slogan = appSettingsService != null ? appSettingsService.getAppSlogan() : "Arte en tus manos ✨";
        String secondaryColor = appSettingsService != null ? appSettingsService.getAppSecondaryColor() : "#6B4C38";
        String configuredWa = appSettingsService != null ? appSettingsService.getWhatsappNumber() : null;
        String whatsappNumber;
        if (configuredWa != null && !configuredWa.isBlank()) {
            whatsappNumber = configuredWa;
        } else if (tenant.getPhone() != null && !tenant.getPhone().isBlank() && !"+56983692046".equals(tenant.getPhone().replaceAll("\\s+", ""))) {
            whatsappNumber = tenant.getPhone();
        } else {
            whatsappNumber = "56964499995";
        }
        String instagramHandle = appSettingsService != null ? appSettingsService.getAppInstagramHandle() : "@bunny.cure";
        boolean bookingEnabled = appSettingsService == null || appSettingsService.isBookingEnabled();
        String morningBlock = appSettingsService != null ? appSettingsService.getMorningBlock() : "09:00 – 13:00";
        String afternoonBlock = appSettingsService != null ? appSettingsService.getAfternoonBlock() : "15:00 – 18:00";
        String nightBlock = appSettingsService != null ? appSettingsService.getNightBlock() : "19:00 – 22:00";

        TenantInfoDto dto = TenantInfoDto.builder()
                .id(tenant.getId())
                .name(name)
                .slug(tenant.getSlug())
                .customDomain(tenant.getCustomDomain())
                .phone(phone)
                .email(email)
                .address(tenant.getAddress())
                .logoUrl(logoUrl)
                .primaryColor(primaryColor)
                .secondaryColor(secondaryColor)
                .slogan(slogan)
                .whatsappNumber(whatsappNumber)
                .instagramHandle(instagramHandle)
                .bookingEnabled(bookingEnabled)
                .morningBlock(morningBlock)
                .afternoonBlock(afternoonBlock)
                .nightBlock(nightBlock)
                .active(tenant.isActive())
                .planTier(tenant.getPlanTier())
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }
}
