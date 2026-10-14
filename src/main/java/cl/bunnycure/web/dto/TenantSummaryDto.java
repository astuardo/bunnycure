package cl.bunnycure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSummaryDto {

    private Long id;
    private String name;
    private String slug;
    private String customDomain;
    private boolean active;
    private String planTier;
    private String primaryColor;
    private String logoUrl;
}
