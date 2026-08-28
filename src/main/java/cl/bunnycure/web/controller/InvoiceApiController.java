package cl.bunnycure.web.controller;

import cl.bunnycure.service.ApiGatewaySiiService;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.InvoiceQuotaResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Invoices", description = "Operaciones de facturación y Boletas de Honorarios Electrónicas (SII v2 vía ApiGateway)")
public class InvoiceApiController {

    private final ApiGatewaySiiService apiGatewaySiiService;

    @Operation(summary = "Obtener resumen de boletas del mes", description = "Retorna la cantidad de boletas emitidas en el mes actual.")
    @GetMapping("/quota")
    public ResponseEntity<ApiResponse<InvoiceQuotaResponseDto>> getQuota() {
        long generatedThisMonth = apiGatewaySiiService.getGeneratedInvoicesThisMonth();
        
        InvoiceQuotaResponseDto dto = InvoiceQuotaResponseDto.builder()
                .generatedThisMonth(generatedThisMonth)
                .monthlyLimit(9999) // Sin límite artificial en ApiGateway
                .remainingThisMonth(9999)
                .generateByDefault(true)
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(summary = "Listar boletas de honorarios emitidas en el SII", description = "Consulta directamente al SII vía ApiGateway las BHE emitidas en un período.")
    @PreAuthorize("hasAnyRole('SALON_ADMIN','ADMIN','SUPER_ADMIN')")
    @GetMapping("/issued")
    public ResponseEntity<ApiResponse<JsonNode>> listIssued(
            @Parameter(description = "Período en formato YYYYMM (ej. 202608) o diario YYYYMMDD")
            @RequestParam(required = false) String periodo,
            @Parameter(description = "Número de página (default 1)")
            @RequestParam(defaultValue = "1") int pagina) {

        String effectivePeriodo = (periodo != null && !periodo.isBlank()) 
                ? periodo 
                : LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));

        JsonNode response = apiGatewaySiiService.listIssuedInvoices(effectivePeriodo, pagina);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Reenviar correo oficial de BHE por el SII", description = "Envía la boleta con PDF y XML oficial directamente desde la plataforma del SII.")
    @PreAuthorize("hasAnyRole('SALON_ADMIN','ADMIN','SUPER_ADMIN')")
    @PostMapping("/{codigo}/email")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resendEmail(
            @PathVariable String codigo,
            @RequestBody(required = false) Map<String, String> body) {

        String recipientEmail = (body != null) ? body.get("email") : null;
        boolean sent = apiGatewaySiiService.sendInvoiceEmail(codigo, recipientEmail);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "success", sent,
                "codigo", codigo,
                "recipient", recipientEmail != null ? recipientEmail : "email oficial SII"
        )));
    }

    @Operation(summary = "Descargar PDF oficial de BHE", description = "Descarga el archivo binario PDF generado oficialmente por el SII.")
    @PreAuthorize("hasAnyRole('SALON_ADMIN','ADMIN','SUPER_ADMIN','RECEPTIONIST')")
    @GetMapping("/{codigo}/pdf")
    public ResponseEntity<byte[]> getPdf(@PathVariable String codigo) {
        byte[] pdfBytes = apiGatewaySiiService.getInvoicePdf(codigo);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "bhe-" + codigo + ".pdf");
        headers.setContentLength(pdfBytes != null ? pdfBytes.length : 0);

        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    @Operation(summary = "Anular boleta de honorarios en el SII", description = "Anula una BHE emitida en el SII indicando la causa.")
    @PreAuthorize("hasAnyRole('SALON_ADMIN','ADMIN','SUPER_ADMIN')")
    @PostMapping("/{folio}/cancel")
    public ResponseEntity<ApiResponse<JsonNode>> cancel(
            @PathVariable Long folio,
            @Parameter(description = "Causa: 1=No pago, 2=No prestación de servicio, 3=Error digitación (default 3)")
            @RequestParam(defaultValue = "3") String causa) {

        JsonNode result = apiGatewaySiiService.cancelInvoice(folio, causa);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
