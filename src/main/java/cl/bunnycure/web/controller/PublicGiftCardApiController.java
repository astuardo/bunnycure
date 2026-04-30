package cl.bunnycure.web.controller;

import cl.bunnycure.service.GiftCardService;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.GiftCardRedeemRequestDto;
import cl.bunnycure.web.dto.GiftCardResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Public GiftCards", description = "API pública para consulta/canje por QR")
@RestController
@RequestMapping("/api/public/giftcards")
@RequiredArgsConstructor
public class PublicGiftCardApiController {

    private final GiftCardService giftCardService;

    @Operation(summary = "Obtener GiftCard por código (público)")
    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<GiftCardResponseDto>> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(giftCardService.getByCodePublic(code)));
    }

    @Operation(summary = "Canjear GiftCard por código + PIN (público)")
    @PostMapping("/{code}/redeem")
    public ResponseEntity<ApiResponse<GiftCardResponseDto>> redeem(
            @PathVariable String code,
            @Valid @RequestBody GiftCardRedeemRequestDto request,
            HttpServletRequest httpRequest
    ) {
        GiftCardResponseDto redeemed = giftCardService.redeemPublic(
                code,
                request,
                resolveIp(httpRequest),
                resolveUserAgent(httpRequest)
        );
        return ResponseEntity.ok(ApiResponse.success(redeemed));
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
}
