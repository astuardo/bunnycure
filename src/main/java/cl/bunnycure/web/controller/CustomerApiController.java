package cl.bunnycure.web.controller;

import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.service.CustomerService;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.CustomerDto;
import cl.bunnycure.web.dto.CustomerLookupResponseDto;
import cl.bunnycure.web.dto.CustomerSummary;
import cl.bunnycure.web.dto.ErrorResponse;
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
 * REST API Controller para gestión de clientes.
 * Expone endpoints CRUD completos con documentación OpenAPI.
 */
@Slf4j
@Tag(name = "Customers", description = "API para gestión de clientes")
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerApiController {

    private final CustomerService customerService;

    @Operation(
            summary = "Listar clientes",
            description = """
                    Obtiene lista de todos los clientes o filtra por búsqueda de texto.
                    La búsqueda es case-insensitive y busca en el nombre completo.
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lista de clientes obtenida exitosamente",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = CustomerSummary.class))
                    )
            )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerSummary>>> list(
            @Parameter(description = "Texto de búsqueda en nombre del cliente")
            @RequestParam(required = false) String search) {
        
        List<Customer> customers;
        
        if (search != null && !search.isBlank()) {
            customers = customerService.search(search);
        } else {
            customers = customerService.findAll();
        }
        
        List<CustomerSummary> summaries = customers.stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(summaries));
    }

    @Operation(
            summary = "Obtener cliente por ID",
            description = "Obtiene los detalles completos de un cliente específico.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Cliente encontrado",
                    content = @Content(schema = @Schema(implementation = CustomerDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Cliente no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerDto>> getById(
            @Parameter(description = "ID del cliente", required = true)
            @PathVariable Long id) {
        
        Customer customer = customerService.findById(id);
        CustomerDto dto = toDto(customer);
        
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(
            summary = "Crear nuevo cliente",
            description = """
                    Crea un nuevo cliente en el sistema.
                    El teléfono debe ser único. El email es opcional pero también debe ser único si se proporciona.
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Cliente creado exitosamente",
                    content = @Content(schema = @Schema(implementation = CustomerDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Datos inválidos o teléfono/email duplicado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerDto>> create(
            @Valid @RequestBody CustomerDto request) {
        
        log.info("[API] Creating customer: {}", request.getFullName());
        
        Customer created = customerService.create(request);
        CustomerDto dto = toDto(created);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(dto));
    }

    @Operation(
            summary = "Actualizar cliente existente",
            description = "Actualiza los datos de un cliente existente.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Cliente actualizado exitosamente",
                    content = @Content(schema = @Schema(implementation = CustomerDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Cliente no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerDto>> update(
            @Parameter(description = "ID del cliente", required = true)
            @PathVariable Long id,
            
            @Valid @RequestBody CustomerDto request) {
        
        log.info("[API] Updating customer {}", id);
        
        Customer updated = customerService.update(id, request);
        CustomerDto dto = toDto(updated);
        
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(
            summary = "Eliminar cliente",
            description = """
                    Elimina permanentemente un cliente del sistema.
                    ADVERTENCIA: Esta operación puede fallar si el cliente tiene citas o solicitudes asociadas.
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204",
                    description = "Cliente eliminado exitosamente"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Cliente no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "No se puede eliminar: cliente tiene datos asociados",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "ID del cliente", required = true)
            @PathVariable Long id) {
        
        log.info("[API] Deleting customer {}", id);
        
        customerService.delete(id);
        
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Buscar cliente por teléfono",
            description = """
                    Busca un cliente existente por su número de teléfono.
                    Utilizado por el portal público de reservas para auto-completar datos.
                    
                    Retorna los datos del cliente si existe, o un objeto vacío si no.
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Búsqueda exitosa (cliente encontrado o no encontrado)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CustomerLookupResponseDto.class)
                    )
            )
    })
    @PostMapping("/lookup")
    public ResponseEntity<CustomerLookupResponseDto> lookup(
            @Parameter(description = "Número de teléfono en formato +56XXXXXXXXX", required = true)
            @RequestParam String phone) {
        CustomerLookupResponseDto response = customerService.findByPhoneForLookup(phone);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Ajustar sellos de fidelización", description = "Incrementa o decrementa los sellos de un cliente manualmente.")
    @PostMapping("/{id}/loyalty/adjust")
    public ResponseEntity<ApiResponse<CustomerDto>> adjustLoyalty(
            @PathVariable Long id,
            @RequestParam int delta) {
        
        log.info("[API] Adjusting loyalty for customer {}: delta={}", id, delta);
        Customer updated = customerService.adjustLoyaltyStamps(id, delta);
        return ResponseEntity.ok(ApiResponse.success(toDto(updated)));
    }

    /**
     * Convierte una entidad Customer a CustomerDto.
     */
    private CustomerDto toDto(Customer customer) {
        return CustomerDto.builder()
                .id(customer.getId())
                .publicId(customer.getPublicId())
                .fullName(customer.getFullName())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .gender(customer.getGender())
                .birthDate(customer.getBirthDate())
                .emergencyPhone(customer.getEmergencyPhone())
                .healthNotes(customer.getHealthNotes())
                .notes(customer.getNotes())
                .notificationPreference(customer.getNotificationPreference())
                .loyaltyStamps(customer.getLoyaltyStamps())
                .totalCompletedVisits(customer.getTotalCompletedVisits())
                .build();
    }

    /**
     * Convierte una entidad Customer a CustomerSummary.
     */
    private CustomerSummary toSummary(Customer customer) {
        return new CustomerSummary(customer, 0);
    }
}

