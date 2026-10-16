package cl.bunnycure.web.controller;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.config.TestMailConfiguration;
import cl.bunnycure.domain.model.User;
import cl.bunnycure.domain.model.WebPushSubscription;
import cl.bunnycure.domain.repository.UserRepository;
import cl.bunnycure.domain.repository.WebPushSubscriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestMailConfiguration.class)
@DisplayName("Web Push: acceso autenticado y aislamiento por salón y usuario")
class WebPushSubscriptionSecurityIntegrationTest {

    private static final String OWNER = "push-owner";
    private static final String INTRUDER = "push-intruder";
    private static final String ENDPOINT = "https://web.push.apple.com/test-device-owner";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebPushSubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> userIds = new ArrayList<>();
    private Long ownerId;

    @BeforeEach
    void setUp() {
        ownerId = createUserInTenant1(OWNER);
        createUserInTenant1(INTRUDER);
    }

    @AfterEach
    void tearDown() {
        for (Long tenant : List.of(1L, 2L)) {
            inTenant(tenant, () -> {
                subscriptionRepository.deleteAll(subscriptionRepository.findAll());
                return null;
            });
        }
        inTenant(1L, () -> {
            userRepository.deleteAllById(userIds);
            return null;
        });
        userIds.clear();
    }

    @Test
    @DisplayName("Sin autenticación: solo /public-key responde; registrar, baja, test y debug → 401")
    void anonymousAccess_IsRejectedExceptPublicKey() throws Exception {
        mockMvc.perform(get("/api/push-subscriptions/public-key")).andExpect(status().isOk());

        mockMvc.perform(post("/api/push-subscriptions").contentType(MediaType.APPLICATION_JSON).content(registrationBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/push-subscriptions/unsubscribe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"" + ENDPOINT + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/push-subscriptions/test")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/push-subscriptions/debug")).andExpect(status().isUnauthorized());

        assertThat(inTenant(1L, () -> subscriptionRepository.findByEndpoint(ENDPOINT))).isEmpty();
    }

    @Test
    @DisplayName("Diagnóstico: un usuario sin rol admin recibe 403")
    void debug_RequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/push-subscriptions/debug").with(user(INTRUDER).roles("RECEPTIONIST")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Registro autenticado: la suscripción queda ligada al usuario y a su salón")
    void authenticatedRegistration_BindsUserAndTenant() throws Exception {
        mockMvc.perform(post("/api/push-subscriptions").with(user(OWNER).roles("SALON_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(registrationBody()))
                .andExpect(status().isOk());

        WebPushSubscription saved = inTenant(1L, () -> subscriptionRepository.findByEndpoint(ENDPOINT)).orElseThrow();
        assertThat(saved.getUserId()).isEqualTo(ownerId);
        assertThat(saved.getTenantId()).isEqualTo(1L);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    @DisplayName("Otro usuario no puede dar de baja un dispositivo ajeno")
    void unsubscribe_ByAnotherUser_DoesNotDeactivate() throws Exception {
        mockMvc.perform(post("/api/push-subscriptions").with(user(OWNER).roles("SALON_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(registrationBody()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/push-subscriptions/unsubscribe").with(user(INTRUDER).roles("RECEPTIONIST"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + ENDPOINT + "\"}"))
                .andExpect(status().isOk());

        assertThat(inTenant(1L, () -> subscriptionRepository.findByEndpoint(ENDPOINT)).orElseThrow().isActive()).isTrue();
    }

    @Test
    @DisplayName("Los avisos de un salón solo ven las suscripciones de ese salón")
    void activeSubscriptions_AreScopedByTenant() {
        inTenant(1L, () -> subscriptionRepository.save(subscription("https://push.example/salon-1", ownerId)));
        inTenant(2L, () -> subscriptionRepository.save(subscription("https://push.example/salon-2", 999L)));

        List<String> seenByTenant1 = inTenant(1L, () -> subscriptionRepository.findByActiveTrue().stream()
                .map(WebPushSubscription::getEndpoint).toList());
        List<String> seenByTenant2 = inTenant(2L, () -> subscriptionRepository.findByActiveTrue().stream()
                .map(WebPushSubscription::getEndpoint).toList());

        assertThat(seenByTenant1).contains("https://push.example/salon-1").doesNotContain("https://push.example/salon-2");
        assertThat(seenByTenant2).contains("https://push.example/salon-2").doesNotContain("https://push.example/salon-1");
    }

    private Long createUserInTenant1(String username) {
        Long id = inTenant(1L, () -> {
            User user = new User();
            user.setUsername(username);
            user.setPassword("{noop}irrelevant");
            user.setRole("SALON_ADMIN");
            return userRepository.save(user).getId();
        });
        userIds.add(id);
        return id;
    }

    private static WebPushSubscription subscription(String endpoint, Long userId) {
        return WebPushSubscription.builder()
                .endpoint(endpoint)
                .p256dh("p256dh-key")
                .auth("auth-key")
                .userId(userId)
                .build();
    }

    private static String registrationBody() {
        return "{\"endpoint\":\"" + ENDPOINT + "\",\"keys\":{\"p256dh\":\"p256dh-key\",\"auth\":\"auth-key\"}}";
    }

    private <T> T inTenant(Long tenantId, Supplier<T> action) {
        try {
            TenantContext.setCurrentTenantId(tenantId);
            return transactionTemplate.execute(status -> action.get());
        } finally {
            TenantContext.clear();
        }
    }
}
