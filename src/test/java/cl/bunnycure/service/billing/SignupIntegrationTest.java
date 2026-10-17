package cl.bunnycure.service.billing;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.config.TestMailConfiguration;
import cl.bunnycure.domain.enums.SubscriptionStatus;
import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.repository.EmailVerificationTokenRepository;
import cl.bunnycure.domain.repository.TenantRepository;
import cl.bunnycure.domain.repository.UserRepository;
import cl.bunnycure.service.PlatformMailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestMailConfiguration.class)
@DisplayName("Registro autoservicio: alta, verificación de email, prueba gratuita y login")
class SignupIntegrationTest {

    @Value("${bunnycure.scheduler.timezone:America/Santiago}")
    private String timezone;

    private static final String EMAIL = "duena@salonnuevo.cl";
    private static final String PASSWORD = "ClaveSegura2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EmailVerificationTokenRepository tokenRepository;

    @MockitoBean
    private PlatformMailService platformMailService;

    @BeforeEach
    void setUp() {
        when(platformMailService.getProductName()).thenReturn("BunnyCure");
        when(platformMailService.send(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        tenantRepository.findBySlugIgnoreCase("salon-nuevo").ifPresent(t -> {
            tokenRepository.deleteAll(tokenRepository.findAll().stream().filter(tok -> tok.getTenantId().equals(t.getId())).toList());
            TenantContext.setCurrentTenantId(t.getId());
            userRepository.deleteAll(userRepository.findAll());
            TenantContext.clear();
            tenantRepository.delete(t);
        });
    }

    @Test
    @DisplayName("Flujo completo: registro → login bloqueado → verificar → prueba de 14 días → login OK")
    void fullSignupFlow() throws Exception {
        mockMvc.perform(post("/api/public/signup").contentType(MediaType.APPLICATION_JSON).content(signupJson(EMAIL, PASSWORD, "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("salon-nuevo"));

        Tenant tenant = tenantRepository.findBySlugIgnoreCase("salon-nuevo").orElseThrow();
        assertThat(tenant.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.PENDING_VERIFICATION);
        assertThat(tenant.getPlanTier()).isEqualTo("PRO");
        assertThat(tenant.getTermsVersion()).isNotBlank();
        assertThat(tenant.getTermsAcceptedAt()).isNotNull();

        // La administradora quedó en el salón nuevo (no en el salón 1)
        assertThat(userRepository.findTenantIdByUsername(EMAIL)).contains(tenant.getId());

        // Sin verificar: login bloqueado
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.errorCode").value("EMAIL_NOT_VERIFIED"));

        // El enlace de verificación llega por correo
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(platformMailService).send(eq(EMAIL), anyString(), anyString(), anyString(), anyString(), link.capture());
        assertThat(link.getValue()).contains("/registro/verificar?token=").contains("&salon=salon-nuevo");
        String token = link.getValue().replaceAll(".*token=([^&]+).*", "$1");

        // En la base solo se guarda el hash del token
        assertThat(tokenRepository.findAll()).noneMatch(t -> t.getTokenHash().equals(token));

        mockMvc.perform(post("/api/public/signup/verify").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("salon-nuevo"))
                .andExpect(jsonPath("$.data.trialEndsOn").isNotEmpty());

        Tenant verified = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(verified.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.TRIALING);
        // misma zona que SignupService: en CI (UTC) la fecha del JVM puede ir un día por delante de Chile
        assertThat(verified.getTrialEndsOn()).isAfter(java.time.LocalDate.now(java.time.ZoneId.of(timezone)).plusDays(13));

        // Repetir el enlace (doble clic) no falla
        mockMvc.perform(post("/api/public/signup/verify").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.tenantSlug").value("salon-nuevo"));
    }

    @Test
    @DisplayName("Email ya registrado en la plataforma → 409")
    void duplicateEmail_IsRejected() throws Exception {
        mockMvc.perform(post("/api/public/signup").contentType(MediaType.APPLICATION_JSON).content(signupJson(EMAIL, PASSWORD, "")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/public/signup").contentType(MediaType.APPLICATION_JSON).content(signupJson(EMAIL.toUpperCase(), PASSWORD, "")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Contraseña débil → 400 y no se crea el salón")
    void weakPassword_IsRejected() throws Exception {
        mockMvc.perform(post("/api/public/signup").contentType(MediaType.APPLICATION_JSON).content(signupJson(EMAIL, "solo-letras-sin", "")))
                .andExpect(status().isBadRequest());
        assertThat(tenantRepository.findBySlugIgnoreCase("salon-nuevo")).isEmpty();
    }

    @Test
    @DisplayName("Honeypot completado (bot) → misma respuesta, pero no se crea nada ni se envía correo")
    void honeypot_CreatesNothing() throws Exception {
        mockMvc.perform(post("/api/public/signup").contentType(MediaType.APPLICATION_JSON).content(signupJson(EMAIL, PASSWORD, "http://spam.example")))
                .andExpect(status().isCreated());
        assertThat(tenantRepository.findBySlugIgnoreCase("salon-nuevo")).isEmpty();
        verify(platformMailService, never()).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Sin aceptar los términos → 400")
    void termsNotAccepted_IsRejected() throws Exception {
        String json = signupJson(EMAIL, PASSWORD, "").replace("\"acceptTerms\":true", "\"acceptTerms\":false");
        mockMvc.perform(post("/api/public/signup").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Disponibilidad de dirección: reservadas y existentes no están disponibles")
    void slugAvailability() throws Exception {
        mockMvc.perform(get("/api/public/signup/slug-available").param("slug", "admin"))
                .andExpect(jsonPath("$.data.available").value(false));
        mockMvc.perform(get("/api/public/signup/slug-available").param("slug", "bunnycure"))
                .andExpect(jsonPath("$.data.available").value(false));
        mockMvc.perform(get("/api/public/signup/slug-available").param("slug", "un-salon-libre"))
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    @DisplayName("Catálogo público de planes")
    void publicPlans() throws Exception {
        mockMvc.perform(get("/api/public/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("STANDARD"))
                .andExpect(jsonPath("$.data[1].monthlyPriceClp").value(49900))
                .andExpect(jsonPath("$.data[2].maxStaffSeats").doesNotExist());
    }

    @Test
    @DisplayName("Token inexistente → 400")
    void invalidToken_IsRejected() throws Exception {
        mockMvc.perform(post("/api/public/signup/verify").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"no-existe\"}"))
                .andExpect(status().isBadRequest());
        assertThat(Optional.empty()).isEmpty();
    }

    private static String signupJson(String email, String password, String honeypot) {
        return """
                {"salonName":"Salón Nuevo","ownerName":"Camila Rojas","email":"%s","phone":"+56911112222",
                 "password":"%s","plan":"PRO","acceptTerms":true,"website":"%s"}
                """.formatted(email, password, honeypot);
    }

    private static String loginJson() {
        return "{\"username\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}";
    }
}
