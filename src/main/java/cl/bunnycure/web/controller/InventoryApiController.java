package cl.bunnycure.web.controller;

import cl.bunnycure.domain.model.Product;
import cl.bunnycure.domain.repository.ProductRepository;
import cl.bunnycure.service.InventoryService;
import cl.bunnycure.service.ProductPriceMonitoringService;
import cl.bunnycure.service.PurchaseService;
import cl.bunnycure.domain.repository.InventoryMovementRepository;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.ConsumeRequestDto;
import cl.bunnycure.web.dto.ErrorResponse;
import cl.bunnycure.web.dto.ProductDto;
import cl.bunnycure.web.dto.ProductResponseDto;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "Inventory", description = "Gestión de inventario y consumos")
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryApiController {

    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final ProductPriceMonitoringService productPriceMonitoringService;
    private final PurchaseService purchaseService;
    private final InventoryMovementRepository movementRepository;

    @Operation(summary = "Listar productos de inventario")
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<ProductResponseDto>>> listProducts() {
        List<Product> products = productRepository.findAll();
        List<ProductResponseDto> dtos = products.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @Operation(summary = "Obtener producto por id")
    @GetMapping("/products/{id}")
    public ResponseEntity<ApiResponse<ProductResponseDto>> getProduct(@PathVariable Long id) {
        Product p = productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
        return ResponseEntity.ok(ApiResponse.success(toDto(p)));
    }

    @Operation(summary = "Crear producto")
    @PostMapping("/products")
    public ResponseEntity<ApiResponse<ProductResponseDto>> createProduct(@Valid @RequestBody ProductDto request) {
        Product p = toEntity(request);
        Product saved = productRepository.save(p);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(toDto(saved)));
    }

    @Operation(summary = "Actualizar producto")
    @PutMapping("/products/{id}")
    public ResponseEntity<ApiResponse<ProductResponseDto>> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductDto request) {
        Product existing = productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
        existing.setName(request.getName());
        existing.setPurchasePrice(request.getPurchasePrice());
        existing.setPurchaseUrl(request.getPurchaseUrl());
        existing.setPurchaseUnit(request.getPurchaseUnit());
        existing.setConsumptionUnit(request.getConsumptionUnit());
        existing.setConversionFactor(request.getConversionFactor());
        existing.setStockConsumptionUnit(request.getStockConsumptionUnit());

        Product updated = productRepository.save(existing);
        return ResponseEntity.ok(ApiResponse.success(toDto(updated)));
    }

    @Operation(summary = "Eliminar producto")
    @DeleteMapping("/products/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        try {
            productRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (DataIntegrityViolationException ex) {
            log.warn("No se pudo eliminar producto {}: está en uso", id);
            // Return 400 with error message
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("No se puede eliminar producto: está en uso", "PRODUCT_IN_USE"));
        }
    }

    @Operation(summary = "Actualizar precio observado de un producto")
    @PostMapping("/products/{id}/refresh-observed")
    public ResponseEntity<ApiResponse<ProductResponseDto>> refreshObservedPrice(@PathVariable Long id) {
        Product updated = productPriceMonitoringService.refreshProduct(id);
        return ResponseEntity.ok(ApiResponse.success(toDto(updated)));
    }

    @Operation(summary = "Registrar consumo de materiales para un servicio")
    @PostMapping("/consume")
    public ResponseEntity<ApiResponse<Void>> consumeMaterials(@Valid @RequestBody ConsumeRequestDto request) {
        inventoryService.consumeMaterialsForService(request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "Registrar compra/entrada de inventario")
    @PostMapping("/purchase")
    public ResponseEntity<ApiResponse<cl.bunnycure.domain.model.InventoryMovement>> registerPurchase(@Valid @RequestBody cl.bunnycure.web.dto.PurchaseRequestDto request) {
        cl.bunnycure.domain.model.InventoryMovement m = purchaseService.registerPurchase(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(m));
    }

    @Operation(summary = "Listar movimientos de inventario (por producto)")
    @GetMapping("/movements")
    public ResponseEntity<ApiResponse<List<cl.bunnycure.domain.model.InventoryMovement>>> listMovements(@RequestParam(required = false) Long productId) {
        List<cl.bunnycure.domain.model.InventoryMovement> movements;
        if (productId != null) {
            movements = movementRepository.findByProductIdOrderByCreatedAtDesc(productId);
        } else {
            movements = movementRepository.findAll();
        }
        return ResponseEntity.ok(ApiResponse.success(movements));
    }

    private ProductResponseDto toDto(Product p) {
        return ProductResponseDto.builder()
                .id(p.getId())
                .name(p.getName())
                .purchasePrice(p.getPurchasePrice())
                .purchaseUrl(p.getPurchaseUrl())
                .purchaseUnit(p.getPurchaseUnit())
                .consumptionUnit(p.getConsumptionUnit())
                .conversionFactor(p.getConversionFactor())
                .stockConsumptionUnit(p.getStockConsumptionUnit())
                .observedPrice(p.getObservedPrice())
                .previousObservedPrice(p.getPreviousObservedPrice())
                .observedAvailable(p.getObservedAvailable())
                .lastObservedAt(p.getLastObservedAt())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private Product toEntity(ProductDto dto) {
        Product p = Product.builder()
                .name(dto.getName())
                .purchasePrice(dto.getPurchasePrice())
                .purchaseUrl(dto.getPurchaseUrl())
                .purchaseUnit(dto.getPurchaseUnit())
                .consumptionUnit(dto.getConsumptionUnit())
                .conversionFactor(dto.getConversionFactor())
                .stockConsumptionUnit(dto.getStockConsumptionUnit())
                .build();
        return p;
    }

    // Simple runtime exception to map to 404
    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String msg) { super(msg); }
    }
}
