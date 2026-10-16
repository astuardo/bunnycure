package cl.bunnycure.config;

import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.model.User;
import cl.bunnycure.domain.repository.TenantRepository;
import cl.bunnycure.domain.repository.UserRepository;
import cl.bunnycure.service.JwtService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantContextFilter: Aislamiento multi-tenant contra suplantación de inquilinos")
class TenantContextFilterTest {

    private static final String TOKEN = "valid.jwt.token";
    private static final String USERNAME = "admin";
    private static final Long TOKEN_TENANT_ID = 1L;
    private static final Long OTHER_TENANT_ID = 2L;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    private TenantContextFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    /** Tenant visto por el resto de la cadena; null si la petición fue cortada. */
    private final AtomicReference<Long> tenantSeenByChain = new AtomicReference<>();
    private final FilterChain chain = (req, res) -> tenantSeenByChain.set(TenantContext.getCurrentTenantId());

    @BeforeEach
    void setUp() {
        filter = new TenantContextFilter(tenantRepository, jwtService, userRepository);
        request = new MockHttpServletRequest("GET", "/api/appointments");
        response = new MockHttpServletResponse();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void authenticatedAsTenant1() {
        request.addHeader("Authorization", "Bearer " + TOKEN);
        when(jwtService.validateToken(TOKEN)).thenReturn(true);
        when(jwtService.extractTenantId(TOKEN)).thenReturn(TOKEN_TENANT_ID);
        when(jwtService.extractUsername(TOKEN)).thenReturn(USERNAME);
    }

    private void userInTokenTenantWithRole(String role) {
        User user = new User();
        user.setUsername(USERNAME);
        user.setRole(role);
        when(userRepository.findByUsernameAndTenantId(USERNAME, TOKEN_TENANT_ID)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("Test A: JWT de tenant 1 + X-Tenant-ID: 2 sin SUPER_ADMIN → 403 y la petición no continúa")
    void crossTenantHeader_WithoutSuperAdmin_IsRejectedWith403() throws Exception {
        authenticatedAsTenant1();
        userInTokenTenantWithRole("SALON_ADMIN");
        request.addHeader("X-Tenant-ID", "2");
        when(tenantRepository.existsById(OTHER_TENANT_ID)).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
        assertThat(tenantSeenByChain.get()).as("la cadena de filtros no debe ejecutarse").isNull();
        assertThat(TenantContext.hasExplicitTenant()).as("el contexto debe limpiarse").isFalse();
    }

    @Test
    @DisplayName("Test B: JWT de tenant 1 + X-Tenant-ID: 2 con ROLE_SUPER_ADMIN → continúa con tenant 2")
    void crossTenantHeader_WithSuperAdmin_SwitchesContextAndContinues() throws Exception {
        authenticatedAsTenant1();
        userInTokenTenantWithRole("ROLE_SUPER_ADMIN");
        request.addHeader("X-Tenant-ID", "2");
        when(tenantRepository.existsById(OTHER_TENANT_ID)).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tenantSeenByChain.get()).isEqualTo(OTHER_TENANT_ID);
        assertThat(TenantContext.hasExplicitTenant()).as("el contexto debe limpiarse").isFalse();
    }

    @Test
    @DisplayName("Suplantación por X-Tenant-Slug sin SUPER_ADMIN → 403")
    void crossTenantSlugHeader_WithoutSuperAdmin_IsRejectedWith403() throws Exception {
        authenticatedAsTenant1();
        userInTokenTenantWithRole("RECEPTIONIST");
        request.addHeader("X-Tenant-Slug", "salon-b");
        Tenant salonB = mock(Tenant.class);
        when(salonB.getId()).thenReturn(OTHER_TENANT_ID);
        when(tenantRepository.findBySlugIgnoreCase("salon-b")).thenReturn(Optional.of(salonB));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(tenantSeenByChain.get()).isNull();
    }

    @Test
    @DisplayName("Usuario del token inexistente en su tenant → 403 (no se asume SUPER_ADMIN)")
    void crossTenantHeader_WhenUserNotFoundInTokenTenant_IsRejectedWith403() throws Exception {
        authenticatedAsTenant1();
        when(userRepository.findByUsernameAndTenantId(USERNAME, TOKEN_TENANT_ID)).thenReturn(Optional.empty());
        request.addHeader("X-Tenant-ID", "2");
        when(tenantRepository.existsById(OTHER_TENANT_ID)).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(tenantSeenByChain.get()).isNull();
    }

    @Test
    @DisplayName("Header que coincide con el tenant del token → continúa sin consultar el rol")
    void sameTenantHeader_ContinuesWithTokenTenant() throws Exception {
        authenticatedAsTenant1();
        request.addHeader("X-Tenant-ID", "1");
        when(tenantRepository.existsById(TOKEN_TENANT_ID)).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(tenantSeenByChain.get()).isEqualTo(TOKEN_TENANT_ID);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Sin header de tenant → se fuerza el tenant del JWT")
    void noTenantHeader_UsesTokenTenant() throws Exception {
        authenticatedAsTenant1();

        filter.doFilter(request, response, chain);

        assertThat(tenantSeenByChain.get()).isEqualTo(TOKEN_TENANT_ID);
        verify(tenantRepository, never()).findBySlugIgnoreCase(anyString());
        verifyNoInteractions(userRepository);
    }
}
