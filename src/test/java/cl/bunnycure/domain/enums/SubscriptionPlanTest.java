package cl.bunnycure.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Catálogo de planes: precios, límites y funcionalidades publicados en /demo")
class SubscriptionPlanTest {

    @Test
    @DisplayName("Precios mensuales y anuales (20% de descuento)")
    void prices() {
        assertThat(SubscriptionPlan.STANDARD.priceFor(BillingCycle.MONTHLY)).isEqualTo(29_900);
        assertThat(SubscriptionPlan.PRO.priceFor(BillingCycle.MONTHLY)).isEqualTo(49_900);
        assertThat(SubscriptionPlan.VIP.priceFor(BillingCycle.MONTHLY)).isEqualTo(89_900);
        assertThat(SubscriptionPlan.STANDARD.priceFor(BillingCycle.ANNUAL)).isEqualTo(287_040); // 23.920 x 12
        assertThat(SubscriptionPlan.PRO.priceFor(BillingCycle.ANNUAL)).isEqualTo(479_040);      // 39.920 x 12
    }

    @Test
    @DisplayName("Emprendedora: sin WhatsApp ni SII, hasta 2 puestos")
    void standardPlan() {
        assertThat(SubscriptionPlan.STANDARD.getFeatures()).isEmpty();
        assertThat(SubscriptionPlan.STANDARD.getMaxStaffSeats()).isEqualTo(2);
    }

    @Test
    @DisplayName("Studio Pro: automatización completa, hasta 6 puestos, sin multi-sede ni dominio propio")
    void proPlan() {
        assertThat(SubscriptionPlan.PRO.getFeatures()).containsExactlyInAnyOrder(
                PlanFeature.WHATSAPP_AUTOMATION, PlanFeature.SII_INVOICING, PlanFeature.WALLET_PASSES,
                PlanFeature.GIFT_CARDS, PlanFeature.INVENTORY, PlanFeature.COMMISSIONS, PlanFeature.MARKETING_CAMPAIGNS);
        assertThat(SubscriptionPlan.PRO.getMaxStaffSeats()).isEqualTo(6);
    }

    @Test
    @DisplayName("Franquicias: todo e ilimitado")
    void vipPlan() {
        assertThat(SubscriptionPlan.VIP.getFeatures()).containsExactlyInAnyOrder(PlanFeature.values());
        assertThat(SubscriptionPlan.VIP.getMaxStaffSeats()).isNull();
    }

    @Test
    @DisplayName("Plan mínimo que incluye cada funcionalidad")
    void cheapestWith() {
        assertThat(SubscriptionPlan.cheapestWith(PlanFeature.SII_INVOICING)).isEqualTo(SubscriptionPlan.PRO);
        assertThat(SubscriptionPlan.cheapestWith(PlanFeature.CUSTOM_DOMAIN)).isEqualTo(SubscriptionPlan.VIP);
    }

    @Test
    @DisplayName("Tolera nombres comerciales y valores heredados")
    void fromString() {
        assertThat(SubscriptionPlan.fromString("Studio Pro")).isEqualTo(SubscriptionPlan.PRO);
        assertThat(SubscriptionPlan.fromString("franquicias")).isEqualTo(SubscriptionPlan.VIP);
        assertThat(SubscriptionPlan.fromString("emprendedora")).isEqualTo(SubscriptionPlan.STANDARD);
        assertThat(SubscriptionPlan.fromString(null)).isEqualTo(SubscriptionPlan.STANDARD);
        assertThat(SubscriptionPlan.fromString("desconocido")).isEqualTo(SubscriptionPlan.STANDARD);
    }
}
