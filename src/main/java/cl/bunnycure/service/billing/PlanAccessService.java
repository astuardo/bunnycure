package cl.bunnycure.service.billing;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.domain.enums.PlanFeature;
import cl.bunnycure.domain.enums.SubscriptionPlan;
import cl.bunnycure.domain.enums.SubscriptionStatus;
import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.repository.TenantRepository;
import cl.bunnycure.domain.repository.UserRepository;
import cl.bunnycure.exception.PlanRestrictionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Decide qué puede hacer el salón actual (TenantContext) según su plan y el estado de su
 * suscripción. Los rechazos se expresan como {@link PlanRestrictionException} (HTTP 402).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanAccessService {

    public static final String FEATURE_REQUIRED = "PLAN_FEATURE_REQUIRED";
    public static final String LIMIT_REACHED = "PLAN_LIMIT_REACHED";
    public static final String SUBSCRIPTION_SUSPENDED = "SUBSCRIPTION_SUSPENDED";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    public Optional<Tenant> currentTenant() {
        return tenantRepository.findById(TenantContext.getCurrentTenantId());
    }

    public SubscriptionPlan currentPlan() {
        return currentTenant().map(Tenant::getSubscriptionPlan).orElse(SubscriptionPlan.STANDARD);
    }

    public boolean currentTenantHas(PlanFeature feature) {
        return currentPlan().includes(feature);
    }

    public void assertFeature(PlanFeature feature) {
        SubscriptionPlan plan = currentPlan();
        if (plan.includes(feature)) {
            return;
        }
        SubscriptionPlan required = SubscriptionPlan.cheapestWith(feature);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("feature", feature.name());
        details.put("featureName", feature.getDisplayName());
        details.put("currentPlan", plan.name());
        details.put("requiredPlan", required.name());
        details.put("requiredPlanName", required.getDisplayName());
        throw new PlanRestrictionException(FEATURE_REQUIRED,
                "«" + feature.getDisplayName() + "» no está incluido en tu plan " + plan.getDisplayName()
                        + ". Disponible desde el plan " + required.getDisplayName() + ".",
                details);
    }

    /** Antes de habilitar una especialista o recepcionista adicional. */
    public void assertStaffSeatAvailable() {
        SubscriptionPlan plan = currentPlan();
        Integer max = plan.getMaxStaffSeats();
        if (max == null) {
            return;
        }
        long used = userRepository.countEnabledStaffSeats();
        if (used >= max) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("limit", "STAFF_SEATS");
            details.put("max", max);
            details.put("used", used);
            details.put("currentPlan", plan.name());
            throw new PlanRestrictionException(LIMIT_REACHED,
                    "Tu plan " + plan.getDisplayName() + " permite hasta " + max
                            + " especialistas o recepcionistas activas. Mejora tu plan para agregar más.",
                    details);
        }
    }

    public void assertSubscriptionAllowsAccess() {
        Optional<Tenant> tenant = currentTenant();
        if (tenant.isEmpty()) {
            return;
        }
        SubscriptionStatus status = tenant.get().getSubscriptionStatus();
        if (status == null || status.allowsAccess()) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", status.name());
        throw new PlanRestrictionException(SUBSCRIPTION_SUSPENDED,
                status == SubscriptionStatus.PENDING_VERIFICATION
                        ? "Confirma tu email para activar tu salón."
                        : "La suscripción de tu salón está suspendida. Regulariza el pago para seguir usando la plataforma.",
                details);
    }
}
