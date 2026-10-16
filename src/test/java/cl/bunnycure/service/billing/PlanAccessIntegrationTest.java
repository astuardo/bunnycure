package cl.bunnycure.service.billing;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.config.TestMailConfiguration;
import cl.bunnycure.domain.enums.SubscriptionStatus;
import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.model.User;
import cl.bunnycure.domain.repository.TenantRepository;
import cl.bunnycure.domain.repository.UserRepository;
import cl.bunnycure.exception.PlanRestrictionException;
import cl.bunnycure.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestMailConfiguration.class)
@DisplayName("Control por plan y estado de suscripción (HTTP 402)")
class PlanAccessIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserService userService;

    private final List<Long> tenantIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long id : tenantIds) {
            inTenant(id, () -> {
                userRepository.deleteAll(userRepository.findAll());
                return null;
            });
            tenantRepository.deleteById(id);
        }
        tenantIds.clear();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Plan Emprendedora: SII, gift cards, inventario, campañas y WhatsApp → 402 con el plan requerido")
    void standardPlan_BlocksPaidFeatures() throws Exception {
        Long salon = createTenant("STANDARD", SubscriptionStatus.ACTIVE);

        for (String path : List.of("/api/invoices/summary", "/api/giftcards", "/api/inventory/products",
                "/api/marketing/templates", "/api/whatsapp/messages", "/api/whatsapp/outbox")) {
            mockMvc.perform(get(path).with(user("admin").roles("SALON_ADMIN", "ADMIN")).header("X-Tenant-ID", salon))
                    .andExpect(status().isPaymentRequired())
                    .andExpect(jsonPath("$.error.errorCode").value("PLAN_FEATURE_REQUIRED"))
                    .andExpect(jsonPath("$.error.details.requiredPlan").value("PRO"));
        }

        // Lo incluido en todos los planes sigue disponible
        mockMvc.perform(get("/api/customers").with(user("admin").roles("SALON_ADMIN", "ADMIN")).header("X-Tenant-ID", salon))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Plan Studio Pro: las funcionalidades de pago responden normalmente")
    void proPlan_AllowsPaidFeatures() throws Exception {
        Long salon = createTenant("PRO", SubscriptionStatus.ACTIVE);
        mockMvc.perform(get("/api/giftcards").with(user("admin").roles("SALON_ADMIN", "ADMIN")).header("X-Tenant-ID", salon))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Suscripción suspendida: la API del salón responde 402, pero 'Mi suscripción' sigue accesible")
    void suspendedSubscription_BlocksApiButAllowsBilling() throws Exception {
        Long salon = createTenant("PRO", SubscriptionStatus.SUSPENDED);

        mockMvc.perform(get("/api/customers").with(user("admin").roles("SALON_ADMIN", "ADMIN")).header("X-Tenant-ID", salon))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.errorCode").value("SUBSCRIPTION_SUSPENDED"));

        mockMvc.perform(get("/api/billing/subscription").with(user("admin").roles("SALON_ADMIN", "ADMIN")).header("X-Tenant-ID", salon))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.data.planName").value("Studio Pro"));
    }

    @Test
    @DisplayName("'Mi suscripción' es solo para administradoras")
    void billing_RequiresAdminRole() throws Exception {
        Long salon = createTenant("PRO", SubscriptionStatus.ACTIVE);
        mockMvc.perform(get("/api/billing/subscription").with(user("recepcion").roles("RECEPTIONIST")).header("X-Tenant-ID", salon))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Límite de puestos: Emprendedora admite 2 especialistas/recepcionistas activas; las administradoras no cuentan")
    void standardPlan_LimitsStaffSeats() {
        Long salon = createTenant("STANDARD", SubscriptionStatus.ACTIVE);

        inTenant(salon, () -> {
            userService.createUser("admin2-" + salon, "Clave12345", "Admin 2", "a@x.cl", "ADMIN");
            userService.createUser("esp1-" + salon, "Clave12345", "Especialista 1", "e1@x.cl", "SPECIALIST");
            userService.createUser("rec1-" + salon, "Clave12345", "Recepción 1", "r1@x.cl", "RECEPTIONIST");
            return null;
        });

        assertThatThrownBy(() -> inTenant(salon, () ->
                userService.createUser("esp2-" + salon, "Clave12345", "Especialista 2", "e2@x.cl", "SPECIALIST")))
                .isInstanceOf(PlanRestrictionException.class)
                .extracting("errorCode").isEqualTo("PLAN_LIMIT_REACHED");

        // Deshabilitar una libera el puesto
        User esp1 = inTenant(salon, () -> userRepository.findByUsername("esp1-" + salon).orElseThrow());
        inTenant(salon, () -> userService.updateUser(esp1.getId(), null, null, null, false));
        User created = inTenant(salon, () ->
                userService.createUser("esp2-" + salon, "Clave12345", "Especialista 2", "e2@x.cl", "SPECIALIST"));
        assertThat(created.getId()).isNotNull();

        // Reactivar la primera excedería el límite
        assertThatThrownBy(() -> inTenant(salon, () -> userService.updateUser(esp1.getId(), null, null, null, true)))
                .isInstanceOf(PlanRestrictionException.class);
    }

    private Long createTenant(String plan, SubscriptionStatus status) {
        Long id = tenantRepository.save(Tenant.builder()
                .name("Salón " + plan).slug("plan-test-" + System.nanoTime()).active(true)
                .planTier(plan).subscriptionStatus(status).build()).getId();
        tenantIds.add(id);
        return id;
    }

    private <T> T inTenant(Long tenantId, Supplier<T> action) {
        Long previous = TenantContext.getExplicitTenantId();
        try {
            TenantContext.setCurrentTenantId(tenantId);
            return action.get();
        } finally {
            TenantContext.setCurrentTenantId(previous);
        }
    }
}
