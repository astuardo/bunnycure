package cl.bunnycure.web.controller;

import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.service.ServiceCatalogService;
import cl.bunnycure.service.ServiceSupplyService;
import cl.bunnycure.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "Service Catalog", description = "API para gestión del catálogo de servicios e insumos")
@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceCatalogApiController {

    private final ServiceCatalogService serviceCatalogService;
    private final ServiceSupplyService serviceSupplyService;

    @Operation(summary = "Listar servicios activos")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ServiceCatalogResponseDto>>> listServices(
            @Parameter(description = "Si es true, solo retorna activos. Si es false/null, retorna todos")
            @RequestParam(required = false) Boolean activeOnly) {
        
        List<ServiceCatalog> services;
        if (Boolean.TRUE.equals(activeOnly)) {
            services = serviceCatalogService.findAllActive();
        } else {
            services = serviceCatalogService.findAll();
        }
        
        List<ServiceCatalogResponseDto> dtos = services.stream()
                .map(this::toResponseDto)
                .toList();
        
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @Operation(summary = "Obtener servicio por ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceCatalogResponseDto>> getById(
            @Parameter(description = "ID del servicio", required = true)
            @PathVariable Long id) {
        
        ServiceCatalog service = serviceCatalogService.findById(id);
        ServiceCatalogResponseDto dto = toResponseDto(service);
        
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(summary = "Crear nuevo servicio")
    @PostMapping
    public ResponseEntity<ApiResponse<ServiceCatalogResponseDto>> create(
            @Valid @RequestBody ServiceCatalogDto request) {
        
        log.info("[API] Creating service: {}", request.getName());
        
        ServiceCatalog saved = serviceCatalogService.save(request);
        ServiceCatalogResponseDto dto = toResponseDto(saved);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(dto));
    }

    @Operation(summary = "Actualizar servicio")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceCatalogResponseDto>> update(
            @Parameter(description = "ID del servicio", required = true)
            @PathVariable Long id,
            @Valid @RequestBody ServiceCatalogDto request) {
        
        log.info("[API] Updating service {}", id);
        request.setId(id);
        
        ServiceCatalog updated = serviceCatalogService.save(request);
        ServiceCatalogResponseDto dto = toResponseDto(updated);
        
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(summary = "Cambiar estado activo/inactivo de servicio")
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<ApiResponse<ServiceCatalogResponseDto>> toggleActive(
            @Parameter(description = "ID del servicio", required = true)
            @PathVariable Long id) {
        
        log.info("[API] Toggling active status for service {}", id);
        serviceCatalogService.toggleActive(id);
        ServiceCatalog updated = serviceCatalogService.findById(id);
        ServiceCatalogResponseDto dto = toResponseDto(updated);
        
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(summary = "Eliminar servicio")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceCatalogResponseDto>> delete(
            @Parameter(description = "ID del servicio", required = true)
            @PathVariable Long id) {
        
        log.info("[API] Deleting service {}", id);
        ServiceCatalogService.DeleteOutcome outcome = serviceCatalogService.delete(id);
        
        if (outcome == ServiceCatalogService.DeleteOutcome.DELETED) {
            return ResponseEntity.noContent().build();
        } else {
            ServiceCatalog service = serviceCatalogService.findById(id);
            ServiceCatalogResponseDto dto = toResponseDto(service);
            return ResponseEntity.ok(ApiResponse.success(dto));
        }
    }

    @Operation(summary = "Obtener insumos / receta de un servicio")
    @GetMapping("/{id}/supplies")
    public ResponseEntity<ApiResponse<List<ServiceSupplyResponseDto>>> getSupplies(@PathVariable Long id) {
        List<ServiceSupplyResponseDto> supplies = serviceSupplyService.getSuppliesForService(id);
        return ResponseEntity.ok(ApiResponse.success(supplies));
    }

    @Operation(summary = "Guardar/actualizar insumos / receta de un servicio")
    @PutMapping("/{id}/supplies")
    public ResponseEntity<ApiResponse<List<ServiceSupplyResponseDto>>> saveSupplies(
            @PathVariable Long id,
            @Valid @RequestBody List<ServiceSupplyDto> supplies) {
        List<ServiceSupplyResponseDto> saved = serviceSupplyService.saveSuppliesForService(id, supplies);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @Operation(summary = "Obtener desglose de costos y margen de un servicio")
    @GetMapping("/{id}/cost-summary")
    public ResponseEntity<ApiResponse<ServiceCostSummaryDto>> getCostSummary(@PathVariable Long id) {
        ServiceCostSummaryDto summary = serviceSupplyService.getCostSummaryForService(id);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @Operation(summary = "Obtener resumen de costos y márgenes de todos los servicios")
    @GetMapping("/costs-summary")
    public ResponseEntity<ApiResponse<List<ServiceCostSummaryDto>>> getAllCostsSummary() {
        List<ServiceCostSummaryDto> summaryList = serviceSupplyService.getAllServicesCostSummary();
        return ResponseEntity.ok(ApiResponse.success(summaryList));
    }

    private ServiceCatalogResponseDto toResponseDto(ServiceCatalog service) {
        return ServiceCatalogResponseDto.builder()
                .id(service.getId())
                .name(service.getName())
                .description(service.getDescription())
                .durationMinutes(service.getDurationMinutes())
                .price(service.getPrice())
                .active(service.isActive())
                .displayOrder(service.getDisplayOrder())
                .imageUrl(null)
                .compatibleServiceIds(service.getCompatibleServices() != null
                        ? service.getCompatibleServices().stream().map(ServiceCatalog::getId).toList()
                        : List.of())
                .build();
    }
}
