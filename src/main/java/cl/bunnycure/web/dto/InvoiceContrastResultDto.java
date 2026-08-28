package cl.bunnycure.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceContrastResultDto {
    private String periodo;
    private LocalDateTime queriedAt;
    private boolean fromCache;
    private int siiTotalCount;
    private BigDecimal siiTotalAmount;
    private int localTotalCount;
    private BigDecimal localTotalAmount;
    private int matchedCount;
    private int pendingEmitCount;
    private int siiOnlyCount;
    private JsonNode rawSiiResponse;
    private List<InvoiceIssuedItemDto> localInvoices;
    private List<InvoicePendingAppointmentDto> pendingAppointments;
}
