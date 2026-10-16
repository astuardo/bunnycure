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
    /** Nombre comercial del plan (Emprendedora, Studio Pro, Franquicias). */
    private String planName;
    /** Funcionalidades de pago incluidas en el plan (códigos de PlanFeature). */
    private java.util.List<String> features;
    /** Estado de la suscripción (TRIALING, ACTIVE, PAST_DUE, SUSPENDED...). */
    private String subscriptionStatus;
}
