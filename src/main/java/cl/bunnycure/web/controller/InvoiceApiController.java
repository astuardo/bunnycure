package cl.bunnycure.web.controller;

import cl.bunnycure.service.ApiGatewaySiiService;
import cl.bunnycure.web.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Invoices", description = "Operaciones de facturación y Boletas de Honorarios Electrónicas (SII v2 vía ApiGateway)")
public class InvoiceApiController {

    private final ApiGatewaySiiService apiGatewaySiiService;

    @Operation(summary = "Obtener resumen y KPIs de boletas del mes", description = "Retorna métricas generales de boletas emitidas, pendientes, montos y estado de conexión (100% datos locales).")
    @PreAuthorize("hasAnyRole('SALON_ADMIN','ADMIN','SUPER_ADMIN','RECEPTIONIST')")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<InvoiceSummaryDto>> getSummary() {
        InvoiceSummaryDto summary = apiGatewaySiiService.getSummary();
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @Operation(summary = "Obtener cuota mensual de boletas", description = "Entrega compatibilidad para cuota de emisión.")
    @GetMapping("/quota")
    public ResponseEntity<ApiResponse<InvoiceQuotaResponseDto>> getQuota() {
        long generatedThisMonth = apiGatewaySiiService.getGeneratedInvoicesThisMonth();

        InvoiceQuotaResponseDto dto = InvoiceQuotaResponseDto.builder()
                .generatedThisMonth(generatedThisMonth)
                .monthlyLimit(9999)
                .remainingThisMonth(9999)
                .generateByDefault(true)
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(summary = "Listar citas con boletas pendientes o fallidas por emitir", description = "Trazabilidad de citas completadas que no tienen boleta emitida con éxito (100% datos locales).")
    @PreAuthorize("hasAnyRole('SALON_ADMIN','ADMIN','SUPER_ADMIN','RECEPTIONIST')")
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<InvoicePendingAppointmentDto>>> getPendingInvoices(
            @Parameter(description = "Fecha inicio (opcional)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @Parameter(description = "Fecha fin (opcional)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        List<InvoicePendingAppointmentDto> pending = apiGatewaySiiService.getPendingInvoices(start, end);
        return ResponseEntity.ok(ApiResponse.success(pending));
    }

    @Operation(summary = "Listar boletas emitidas registradas localmente", description = "Retorna el historial de boletas emitidas para un período específico (100% datos locales).")
    @PreAuthorize("hasAnyRole('SALON_ADMIN','ADMIN','SUPER_ADMIN','RECEPTIONIST')")
    @GetMapping("/local-issued")
    public ResponseEntity<ApiResponse<List<InvoiceIssuedItemDto>>> getLocalIssued(
            @Parameter(description = "Período en formato YYYYMM (ej. 202608)")
            @RequestParam(required = false) String periodo) {

        String effectivePeriodo = (periodo != null && !periodo.isBlank())
                ? periodo
                : LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));

        List<InvoiceIssuedItemDto> issued = apiGatewaySiiService.getLocalIssuedInvoices(effectivePeriodo);
        return ResponseEntity.ok(ApiResponse.success(issued));
    }

    @Operation(summary = "Emitir o reintentar boleta para una cita completada", description = "Emite la BHE en el SII para la cita, permitiendo actualizar opcionalmente RUT y correo.")
    @PreAuthorize("hasAnyRole('SALON_ADMIN','ADMIN','SUPER_ADMIN','RECEPTIONIST')")
    @PostMapping("/appointments/{id}/emit")
    public ResponseEntity<ApiResponse<InvoiceIssuedItemDto>> emitForAppointment(
            @PathVariable Long id,
            @RequestBody(required = false) EmitInvoiceRequestDto requestDto) {

        String rut = requestDto != null ? requestDto.getCustomerRut() : null;
        String email = requestDto != null ? requestDto.getCustomerEmail() : null;

        InvoiceIssuedItemDto result = apiGatewaySiiService.emitInvoiceForAppointment(id, rut, email);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Contraste y conciliación bajo demanda con el SII", description = "Compara las boletas emitidas locales y citas contra los documentos reportados por el SII (con caché).")
    @PreAuthorize("hasAnyRole('SALON_ADMIN','ADMIN','SUPER_ADMIN')")
    @GetMapping("/contrast")
    public ResponseEntity<ApiResponse<InvoiceContrastResultDto>> contrastWithSii(
            @Parameter(description = "Período en formato YYYYMM (ej. 202608)")
            @RequestParam(required = false) String periodo,
            @Parameter(description = "Forzar consulta externa al SII ignorando caché (consume créditos)")
            @RequestParam(defaultValue = "false") boolean forceRefresh) {

        InvoiceContrastResultDto result = apiGatewaySiiService.contrastWithSii(periodo, forceRefresh);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Listar boletas de honorarios emitidas en el SII", description = "Consulta directamente al SII vía ApiGateway las BHE emitidas en un período.")
    @PreAuthorize("hasAnyRole('SALON_ADMIN','ADMIN','SUPER_ADMIN')")
    @GetMapping("/issued")
    public ResponseEntity<ApiResponse<JsonNode>> listIssued(
            @Parameter(description = "Período en formato YYYYMM (ej. 202608) o diario YYYYMMDD")
            @RequestParam(required = false) String periodo,
            @Parameter(description = "Número de página (default 1)")
            @RequestParam(defaultValue = "1") int pagina,
            @Parameter(description = "Forzar recarga ignorando caché")
            @RequestParam(defaultValue = "false") boolean forceRefresh) {

        String effectivePeriodo = (periodo != null && !periodo.isBlank())
                ? periodo
                : LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));

        JsonNode response = apiGatewaySiiService.listIssuedInvoices(effectivePeriodo, pagina, forceRefresh);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Reenviar correo oficial de BHE por el SII", description = "Envía la boleta con PDF y XML oficial directamente desde la plataforma del SII.")
    @PreAuthorize("hasAnyRole('SALON_ADMIN','ADMIN','SUPER_ADMIN','RECEPTIONIST')")
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
