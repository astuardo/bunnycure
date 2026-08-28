package cl.bunnycure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceSummaryDto {
    private long generatedThisMonth;
    private long pendingInvoicesCount;
    private long failedInvoicesCount;
    private BigDecimal totalAmountMonth;
    private boolean apiGatewayConfigured;
    private String emisorRut;
    private boolean sendEmailEnabled;
}
