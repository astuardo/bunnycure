package cl.bunnycure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO para la visualización pública de la tarjeta digital de fidelización (PWA Pass).
 * Compatible con iPhone (Safari A2HS), Android y navegadores de escritorio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyCardPublicDto implements Serializable {

    private String publicId;
    private String customerName;
    private String firstName;
    private String phone;
    private Integer loyaltyStamps;
    private Integer totalCompletedVisits;
    private Integer maxStamps;
    private Integer stampsRemaining;
    private Integer progressPercent;
    private String currentRewardName;
    private boolean rewardAvailable;
    private String rewardStatusMessage;
    private String qrValue;
    private String googleWalletUrl;
    private String salonName;
    private String salonInstagram;
    private String salonWhatsapp;
    private String tenantSlug;
    private String logoUrl;
    private String primaryColor;
}
