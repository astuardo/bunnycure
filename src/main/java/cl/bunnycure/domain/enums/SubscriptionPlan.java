package cl.bunnycure.domain.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Catálogo de planes comerciales (fuente única de precios, límites y funcionalidades).
 * Corresponde a los planes publicados en /demo; el valor persistido en tenants.plan_tier
 * es el nombre del enum (STANDARD / PRO / VIP).
 */
public enum SubscriptionPlan {

    STANDARD("Emprendedora", 29_900L, 2, EnumSet.noneOf(PlanFeature.class)),

    PRO("Studio Pro", 49_900L, 6, EnumSet.of(
            PlanFeature.WHATSAPP_AUTOMATION,
            PlanFeature.SII_INVOICING,
            PlanFeature.WALLET_PASSES,
            PlanFeature.GIFT_CARDS,
            PlanFeature.INVENTORY,
            PlanFeature.COMMISSIONS,
            PlanFeature.MARKETING_CAMPAIGNS)),

    VIP("Franquicias", 89_900L, null, EnumSet.allOf(PlanFeature.class));

    /** Descuento del pago anual (20%), igual al publicado en /demo. */
    public static final int ANNUAL_DISCOUNT_PERCENT = 20;

    private final String displayName;
    private final long monthlyPriceClp;
    /** Especialistas + recepcionistas activas permitidas; null = ilimitado. */
    private final Integer maxStaffSeats;
    private final Set<PlanFeature> features;

    SubscriptionPlan(String displayName, long monthlyPriceClp, Integer maxStaffSeats, Set<PlanFeature> features) {
        this.displayName = displayName;
        this.monthlyPriceClp = monthlyPriceClp;
        this.maxStaffSeats = maxStaffSeats;
        this.features = Collections.unmodifiableSet(features);
    }

    public String getDisplayName() { return displayName; }
    public long getMonthlyPriceClp() { return monthlyPriceClp; }
    public Integer getMaxStaffSeats() { return maxStaffSeats; }
    public Set<PlanFeature> getFeatures() { return features; }

    public boolean includes(PlanFeature feature) {
        return features.contains(feature);
    }

    /** Precio total de un período de facturación. */
    public long priceFor(BillingCycle cycle) {
        return cycle == BillingCycle.ANNUAL
                ? Math.round(monthlyPriceClp * 12 * (100 - ANNUAL_DISCOUNT_PERCENT) / 100.0)
                : monthlyPriceClp;
    }

    /** El plan más barato que incluye la funcionalidad. */
    public static SubscriptionPlan cheapestWith(PlanFeature feature) {
        for (SubscriptionPlan plan : values()) {
            if (plan.includes(feature)) {
                return plan;
            }
        }
        return VIP;
    }

    /** Tolera los nombres comerciales y valores heredados. Desconocido → STANDARD. */
    public static SubscriptionPlan fromString(String value) {
        if (value == null || value.isBlank()) {
            return STANDARD;
        }
        return switch (value.trim().toUpperCase().replace(' ', '_')) {
            case "PRO", "STUDIO_PRO" -> PRO;
            case "VIP", "FRANQUICIAS", "ENTERPRISE", "VIP_ENTERPRISE" -> VIP;
            default -> STANDARD;
        };
    }
}
