package cl.bunnycure.web.controller;

import cl.bunnycure.service.IncomingWhatsAppMessageService;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.IncomingWhatsAppMessageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Tag(name = "WhatsApp Messages", description = "Bandeja de mensajes entrantes de WhatsApp")
@RestController
@RequestMapping("/api/whatsapp/messages")
@RequiredArgsConstructor
public class IncomingWhatsAppMessageController {

    private final IncomingWhatsAppMessageService messageService;

    @Operation(summary = "Listar mensajes recibidos de WhatsApp")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<IncomingWhatsAppMessageDto>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "false") boolean readOnly) {
        Page<IncomingWhatsAppMessageDto> messages = messageService.getMessages(page, size, unreadOnly, readOnly);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @Operation(summary = "Obtener contador de mensajes no leídos")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUnreadCount() {
        long count = messageService.getUnreadCount();
        return ResponseEntity.ok(ApiResponse.success(Map.of("unreadCount", count)));
    }

    @Operation(summary = "Marcar mensaje individual como leído")
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markAsRead(@PathVariable Long id) {
        boolean updated = messageService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("success", updated)));
    }

    @Operation(summary = "Marcar todos los mensajes como leídos")
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markAllAsRead() {
        int count = messageService.markAllAsRead();
        return ResponseEntity.ok(ApiResponse.success(Map.of("markedCount", count)));
    }

    @Operation(summary = "Marcar todos los mensajes de un número/remitente como leídos")
    @PatchMapping("/by-phone/{phone}/read")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markByPhoneAsRead(@PathVariable String phone) {
        int count = messageService.markByPhoneAsRead(phone);
        return ResponseEntity.ok(ApiResponse.success(Map.of("markedCount", count)));
    }
}
