package cl.bunnycure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantInfoDto {
    private Long id;
    private String name;
    private String slug;
    private String customDomain;
    private String phone;
    private String email;
    private String address;
    private String logoUrl;
    private String primaryColor;
    private boolean active;
    private String planTier;
}
