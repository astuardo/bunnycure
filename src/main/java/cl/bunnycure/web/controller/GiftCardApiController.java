package cl.bunnycure.web.controller;

import cl.bunnycure.domain.enums.GiftCardStatus;
import cl.bunnycure.service.GiftCardService;
import cl.bunnycure.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Tag(name = "GiftCards", description = "API para gestión de GiftCards")
@RestController
@RequestMapping("/api/giftcards")
@RequiredArgsConstructor
public class GiftCardApiController {

    private final GiftCardService giftCardService;

    @Operation(summary = "Listar GiftCards")
    @GetMapping
    public ResponseEntity<ApiResponse<List<GiftCardResponseDto>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) GiftCardStatus status,
            @RequestParam(required = false) LocalDate expiringBefore
    ) {
        return ResponseEntity.ok(ApiResponse.success(giftCardService.list(search, status, expiringBefore)));
    }

    @Operation(summary = "Obtener GiftCard por ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GiftCardResponseDto>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(giftCardService.getById(id)));
    }

    @Operation(summary = "Crear GiftCard")
    @PostMapping
    public ResponseEntity<ApiResponse<GiftCardResponseDto>> create(
            @Valid @RequestBody GiftCardCreateRequestDto request,
            HttpServletRequest httpRequest
    ) {
        AuthContext auth = authContext();
        GiftCardResponseDto created = giftCardService.create(
                request,
                auth.userId(),
                auth.username(),
                resolveIp(httpRequest),
                resolveUserAgent(httpRequest)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @Operation(summary = "Editar GiftCard (solo activa y sin canjes)")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GiftCardResponseDto>> update(
            @PathVariable Long id,
            @Valid @RequestBody GiftCardCreateRequestDto request,
            HttpServletRequest httpRequest
    ) {
        AuthContext auth = authContext();
        GiftCardResponseDto updated = giftCardService.update(
                id,
                request,
                auth.userId(),
                auth.username(),
                resolveIp(httpRequest),
                resolveUserAgent(httpRequest)
        );
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @Operation(summary = "Canjear GiftCard (interno admin)")
    @PostMapping("/{id}/redeem")
    public ResponseEntity<ApiResponse<GiftCardResponseDto>> redeem(
            @PathVariable Long id,
            @Valid @RequestBody GiftCardRedeemRequestDto request,
            HttpServletRequest httpRequest
    ) {
        AuthContext auth = authContext();
        GiftCardResponseDto redeemed = giftCardService.redeemInternal(
                id,
                request,
                auth.userId(),
                auth.username(),
                resolveIp(httpRequest),
                resolveUserAgent(httpRequest)
        );
        return ResponseEntity.ok(ApiResponse.success(redeemed));
    }

    @Operation(summary = "Revertir canje de GiftCard (admin)")
    @PostMapping("/{id}/redeem/revert")
    public ResponseEntity<ApiResponse<GiftCardResponseDto>> revert(
            @PathVariable Long id,
            @Valid @RequestBody GiftCardRevertRequestDto request,
            HttpServletRequest httpRequest
    ) {
        AuthContext auth = authContext();
        GiftCardResponseDto reverted = giftCardService.revertRedeem(
                id,
                request,
                auth.userId(),
                auth.username(),
                resolveIp(httpRequest),
                resolveUserAgent(httpRequest)
        );
        return ResponseEntity.ok(ApiResponse.success(reverted));
    }

    @Operation(summary = "Anular GiftCard (solo si no tiene canjes)")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<GiftCardResponseDto>> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) GiftCardCancelRequestDto request,
            HttpServletRequest httpRequest
    ) {
        AuthContext auth = authContext();
        String note = request != null ? request.getNote() : null;
        GiftCardResponseDto cancelled = giftCardService.cancel(
                id,
                note,
                auth.userId(),
                auth.username(),
                resolveIp(httpRequest),
                resolveUserAgent(httpRequest)
        );
        return ResponseEntity.ok(ApiResponse.success(cancelled));
    }

    private AuthContext authContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : null;
        Long userId = giftCardService.resolveCurrentUserIdOrNull(username);
        return new AuthContext(username, userId);
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveUserAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    private record AuthContext(String username, Long userId) {}
}
