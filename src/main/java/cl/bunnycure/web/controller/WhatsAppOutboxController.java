package cl.bunnycure.web.controller;

import cl.bunnycure.service.WhatsAppOutboxService;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.WhatsAppOutboxMessageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/whatsapp/outbox")
@RequiredArgsConstructor
@Tag(name = "WhatsApp Outbox", description = "Gestión de cola de reintentos y mensajes fallidos de WhatsApp")
public class WhatsAppOutboxController {

    private final WhatsAppOutboxService outboxService;

    @Operation(summary = "Listar mensajes en cola de salida / fallidos")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<WhatsAppOutboxMessageDto>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "true") boolean pendingOnly
    ) {
        Page<WhatsAppOutboxMessageDto> messages = outboxService.getMessages(PageRequest.of(page, size), pendingOnly);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @Operation(summary = "Obtener conteo de mensajes pendientes o fallidos")
    @GetMapping("/pending-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getPendingCount() {
        long count = outboxService.getPendingCount();
        return ResponseEntity.ok(ApiResponse.success(Map.of("pendingCount", count)));
    }

    @Operation(summary = "Reintentar envío de un mensaje individual")
    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<Map<String, Object>>> retryMessage(@PathVariable Long id) {
        log.info("[API] Reintentando envío de mensaje outbox ID {}", id);
        Map<String, Object> result = outboxService.retryMessage(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Reintentar todos los mensajes pendientes o fallidos")
    @PostMapping("/retry-all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> retryAll() {
        log.info("[API] Reintentando envío de todos los mensajes pendientes en outbox");
        Map<String, Object> result = outboxService.retryAll();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Descartar un mensaje individual de la cola")
    @PostMapping("/{id}/discard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> discardMessage(@PathVariable Long id) {
        log.info("[API] Descartando mensaje outbox ID {}", id);
        outboxService.discardMessage(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", id, "discarded", true)));
    }

    @Operation(summary = "Descartar todos los mensajes pendientes de la cola")
    @PostMapping("/discard-all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> discardAll() {
        log.info("[API] Descartando todos los mensajes pendientes de outbox");
        int count = outboxService.discardAll();
        return ResponseEntity.ok(ApiResponse.success(Map.of("discardedCount", count)));
    }
}
