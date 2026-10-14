package cl.bunnycure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponseDto {

    private Long id;
    private String name;
    private String slug;
    private String customDomain;
    private String rut;
    private String phone;
    private String email;
    private String address;
    private String logoUrl;
    private String primaryColor;
    private boolean active;
    private String planTier;
    private String whatsappPhoneId;
    private String siiRut;
    private boolean siiConfigured;
    private String siiActividadEconomica;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Métricas operacionales resumidas del salón
    private long userCount;
    private long appointmentCount;
    private long customerCount;
}
