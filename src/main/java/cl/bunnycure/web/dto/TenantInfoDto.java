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
    private String secondaryColor;
    private String slogan;
    private String whatsappNumber;
    private String instagramHandle;
    private boolean bookingEnabled;
    private String morningBlock;
    private String afternoonBlock;
    private String nightBlock;
    private boolean active;
    private String planTier;
}
