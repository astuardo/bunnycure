package cl.bunnycure.web.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InvoiceQuotaResponseDto {
    long generatedThisMonth;
    int monthlyLimit;
    long remainingThisMonth;
    boolean generateByDefault;
}
