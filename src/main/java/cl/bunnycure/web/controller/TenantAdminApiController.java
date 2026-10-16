package cl.bunnycure.web.controller;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.service.TenantManagementService;
import cl.bunnycure.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/tenants")
@org.springframework.security.access.prepost.PreAuthorize("@platformAccess.isPlatformAdmin(authentication)")
@RequiredArgsConstructor
@Tag(name = "Admin Tenants", description = "API de administración y aprovisionamiento SaaS Multi-Salón")
public class TenantAdminApiController {

    private final TenantManagementService tenantManagementService;

    @Operation(summary = "Listar todos los salones registrados con métricas operacionales")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TenantResponseDto>>> getAllTenants(Authentication authentication) {
        assertSuperAdminOrRootAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.success(tenantManagementService.getAllTenants()));
    }

    @Operation(summary = "Listar resúmenes de salones activos para conmutador rápido de salón")
    @GetMapping("/summaries")
    public ResponseEntity<ApiResponse<List<TenantSummaryDto>>> getTenantSummaries(Authentication authentication) {
        assertSuperAdminOrRootAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.success(tenantManagementService.getTenantSummaries()));
    }

    @Operation(summary = "Obtener detalle completo de un salón")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TenantResponseDto>> getTenantById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        assertSuperAdminOrRootAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.success(tenantManagementService.getTenantById(id)));
    }

    @Operation(summary = "Crear y aprovisionar un nuevo salón SaaS")
    @PostMapping
    public ResponseEntity<ApiResponse<TenantResponseDto>> createTenant(
            @Valid @RequestBody TenantCreateRequestDto request,
            Authentication authentication
    ) {
        assertSuperAdminOrRootAdmin(authentication);
        TenantResponseDto created = tenantManagementService.createTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @Operation(summary = "Actualizar configuración y datos de un salón")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TenantResponseDto>> updateTenant(
            @PathVariable Long id,
            @Valid @RequestBody TenantUpdateRequestDto request,
            Authentication authentication
    ) {
        assertSuperAdminOrRootAdmin(authentication);
        TenantResponseDto updated = tenantManagementService.updateTenant(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @Operation(summary = "Activar o desactivar un salón")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Void>> toggleTenantStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body,
            Authentication authentication
    ) {
        assertSuperAdminOrRootAdmin(authentication);
        Boolean active = body.get("active");
        if (active == null) {
            throw new IllegalArgumentException("El campo 'active' es requerido");
        }
        tenantManagementService.toggleTenantStatus(id, active);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private void assertSuperAdminOrRootAdmin(Authentication authentication) {
        if (authentication == null) {
            throw new AccessDeniedException("No autenticado");
        }
        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        Long currentTenant = TenantContext.getCurrentTenantId();
        boolean isRootAdmin = (currentTenant == null || currentTenant.equals(1L)) &&
                authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isSuperAdmin && !isRootAdmin) {
            log.warn("[TENANT-MGMT] Intento de acceso no autorizado a gestión de salones por usuario {} en tenant {}",
                    authentication.getName(), currentTenant);
            throw new AccessDeniedException("Acceso exclusivo para Super Administrador o Administrador Principal de la plataforma");
        }
    }
}
