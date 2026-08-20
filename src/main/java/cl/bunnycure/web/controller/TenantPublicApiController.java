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

    @Operation(
            summary = "Obtener información pública del salón (Branding)",
            description = "Resuelve y retorna los datos de marca, colores y contacto del salón según el dominio actual o slug."
    )
    @GetMapping("/tenant-info")
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

        TenantInfoDto dto = TenantInfoDto.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .slug(tenant.getSlug())
                .customDomain(tenant.getCustomDomain())
                .phone(tenant.getPhone())
                .email(tenant.getEmail())
                .address(tenant.getAddress())
                .logoUrl(tenant.getLogoUrl())
                .primaryColor(tenant.getPrimaryColor())
                .active(tenant.isActive())
                .planTier(tenant.getPlanTier())
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }
}
