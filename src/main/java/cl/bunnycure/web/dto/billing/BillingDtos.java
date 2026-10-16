package cl.bunnycure.web.dto.billing;

import cl.bunnycure.domain.enums.BillingCycle;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs de planes, suscripción, pagos y registro autoservicio. */
public final class BillingDtos {

    private BillingDtos() {
    }

    public record FeatureDto(String code, String name, boolean included) {
    }

    public record PlanDto(
            String code,
            String name,
            long monthlyPriceClp,
            long annualPriceClp,
            int annualDiscountPercent,
            Integer maxStaffSeats,
            List<FeatureDto> features) {
    }

    public record SubscriptionStatusDto(
            String plan,
            String planName,
            String status,
            String billingCycle,
            long periodPriceClp,
            LocalDate trialEndsOn,
            LocalDate currentPeriodEnd,
            /** Último día de gracia antes de suspender (solo PAST_DUE). */
            LocalDate graceEndsOn,
            Integer maxStaffSeats,
            long usedStaffSeats,
            List<FeatureDto> features,
            String paymentProvider,
            boolean onlinePaymentEnabled,
            String manualPaymentInstructions) {
    }

    public record SubscriptionPaymentDto(
            Long id,
            long amountClp,
            String plan,
            String billingCycle,
            LocalDate periodStart,
            LocalDate periodEnd,
            String provider,
            String reference,
            LocalDateTime paidAt) {
    }

    /** Pago manual registrado desde la consola de plataforma. */
    public record ManualPaymentRequest(
            /** Plan pagado; si se omite se mantiene el actual. */
            String plan,
            @NotNull BillingCycle billingCycle,
            /** Monto recibido; si se omite se usa el precio de lista. */
            @Positive Long amountClp,
            @Size(max = 200) String reference) {
    }

    /** Ajustes de la suscripción desde la consola de plataforma (campos opcionales). */
    public record SubscriptionUpdateRequest(
            String plan,
            BillingCycle billingCycle,
            String status,
            LocalDate currentPeriodEnd,
            LocalDate trialEndsOn) {
    }

    public record CheckoutRequest(@NotBlank String plan, @NotNull BillingCycle billingCycle) {
    }

    public record CheckoutResponse(String checkoutUrl) {
    }

    public record SignupRequest(
            @NotBlank @Size(min = 2, max = 120) String salonName,
            @NotBlank @Size(min = 2, max = 100) String ownerName,
            @NotBlank @Email @Size(max = 50) String email,
            @Size(max = 30) String phone,
            @NotBlank @Size(min = 10, max = 100) String password,
            String plan,
            /** Slug deseado; si se omite se genera desde el nombre. */
            @Pattern(regexp = "^$|^[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])$", message = "Solo minúsculas, números y guiones (3 a 40 caracteres)")
            String slug,
            boolean acceptTerms,
            /** Honeypot anti-bots: debe venir vacío. */
            String website) {

        @AssertTrue(message = "Debes aceptar los términos de servicio y la política de privacidad")
        public boolean isTermsAccepted() {
            return acceptTerms;
        }
    }

    public record SignupResponse(String slug, String email, String message) {
    }

    public record VerifyEmailRequest(@NotBlank String token) {
    }

    public record VerifyEmailResponse(String slug, String loginUrl, LocalDate trialEndsOn) {
    }

    public record SlugAvailabilityResponse(String slug, boolean available) {
    }
}
