package cl.bunnycure.web.controller;

import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.service.ServiceCatalogService;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.ErrorResponse;
import cl.bunnycure.web.dto.ServiceCatalogDto;
import cl.bunnycure.web.dto.ServiceCatalogResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API Controller para gestión del catálogo de servicios.
 * Expone endpoints CRUD completos con documentación OpenAPI.
 */
@Slf4j
@Tag(name = "Services", description = "API para gestión del catálogo de servicios")
@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceCatalogApiController {

    private final ServiceCatalogService serviceCatalogService;

    @Operation(
            summary = "Listar servicios",
            description = """
                    Obtiene lista de todos los servicios del catálogo.
                    Por defecto retorna solo servicios activos. Use activeOnly=false para incluir inactivos.
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lista de servicios obtenida exitosamente",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ServiceCatalogResponseDto.class))
                    )
            )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<ServiceCatalogResponseDto>>> list(
            @Parameter(description = "Mostrar solo servicios activos")
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        
        List<ServiceCatalog> services;
        
        if (activeOnly) {
            services = serviceCatalogService.findAllActive();
        } else {
            services = serviceCatalogService.findAll();
        }
        
        List<ServiceCatalogResponseDto> dtos = services.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @Operation(
            summary = "Obtener servicio por ID",
            description = "Obtiene los detalles completos de un servicio específico.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Servicio encontrado",
                    content = @Content(schema = @Schema(implementation = ServiceCatalogResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Servicio no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceCatalogResponseDto>> getById(
            @Parameter(description = "ID del servicio", required = true)
            @PathVariable Long id) {
        
        ServiceCatalog service = serviceCatalogService.findById(id);
        ServiceCatalogResponseDto dto = toResponseDto(service);
        
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(
            summary = "Crear nuevo servicio",
            description = """
                    Crea un nuevo servicio en el catálogo.
                    El servicio se crea activo por defecto.
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Servicio creado exitosamente",
                    content = @Content(schema = @Schema(implementation = ServiceCatalogResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping
    public ResponseEntity<ApiResponse<ServiceCatalogResponseDto>> create(
            @Valid @RequestBody ServiceCatalogDto request) {
        
        log.info("[API] Creating service: {}", request.getName());
        
        ServiceCatalog created = serviceCatalogService.save(request);
        ServiceCatalogResponseDto dto = toResponseDto(created);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(dto));
    }

    @Operation(
            summary = "Actualizar servicio existente",
            description = "Actualiza los datos de un servicio existente del catálogo.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Servicio actualizado exitosamente",
                    content = @Content(schema = @Schema(implementation = ServiceCatalogResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Servicio no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceCatalogResponseDto>> update(
            @Parameter(description = "ID del servicio", required = true)
            @PathVariable Long id,
            
            @Valid @RequestBody ServiceCatalogDto request) {
        
        log.info("[API] Updating service {}", id);
        
        // Asegurar que el ID está en el DTO
        request.setId(id);
        
        ServiceCatalog updated = serviceCatalogService.save(request);
        ServiceCatalogResponseDto dto = toResponseDto(updated);
        
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(
            summary = "Activar/Desactivar servicio",
            description = """
                    Cambia el estado activo/inactivo de un servicio.
                    Los servicios inactivos no aparecen en el portal público pero se mantienen en el sistema.
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Estado cambiado exitosamente",
                    content = @Content(schema = @Schema(implementation = ServiceCatalogResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Servicio no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
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

    @Operation(
            summary = "Eliminar servicio",
            description = """
                    Elimina un servicio del catálogo.
                    Si el servicio está siendo usado en citas o solicitudes, se DESACTIVA en lugar de eliminarse.
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204",
                    description = "Servicio eliminado exitosamente"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Servicio desactivado (no se pudo eliminar porque está en uso)",
                    content = @Content(schema = @Schema(implementation = ServiceCatalogResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Servicio no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceCatalogResponseDto>> delete(
            @Parameter(description = "ID del servicio", required = true)
            @PathVariable Long id) {
        
        log.info("[API] Deleting service {}", id);
        
        ServiceCatalogService.DeleteOutcome outcome = serviceCatalogService.delete(id);
        
        if (outcome == ServiceCatalogService.DeleteOutcome.DELETED) {
            // Eliminado completamente
            return ResponseEntity.noContent().build();
        } else {
            // Desactivado porque está en uso
            ServiceCatalog service = serviceCatalogService.findById(id);
            ServiceCatalogResponseDto dto = toResponseDto(service);
            
            return ResponseEntity.ok(
                    ApiResponse.success(dto)
            );
        }
    }

    /**
     * Convierte una entidad ServiceCatalog a ServiceCatalogResponseDto.
     */
    private ServiceCatalogResponseDto toResponseDto(ServiceCatalog service) {
        return ServiceCatalogResponseDto.builder()
                .id(service.getId())
                .name(service.getName())
                .description(service.getDescription())
                .durationMinutes(service.getDurationMinutes())
                .price(service.getPrice())
                .active(service.isActive())
                .displayOrder(service.getDisplayOrder())
                .imageUrl(null) // imageUrl no existe en el modelo, por ahora null
                .compatibleServiceIds(service.getCompatibleServices().stream().map(ServiceCatalog::getId).toList())
                .build();
    }
}
