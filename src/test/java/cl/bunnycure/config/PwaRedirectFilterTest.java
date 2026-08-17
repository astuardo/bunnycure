package cl.bunnycure.config;

import cl.bunnycure.service.PwaRedirectService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PwaRedirectFilter: Intercepción y emisión de HTTP 301")
class PwaRedirectFilterTest {

    @Mock
    private PwaRedirectService pwaRedirectService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private PwaRedirectFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("Debe emitir 301 y Location para rutas de vistas cuando la redirección está habilitada")
    void doFilterInternal_ShouldRedirect301_WhenEnabledAndLegacyPath() throws Exception {
        request.setMethod("GET");
        request.setRequestURI("/dashboard");

        when(pwaRedirectService.isRedirectionEnabled()).thenReturn(true);
        when(pwaRedirectService.isLegacyMonolithPath("/dashboard")).thenReturn(true);
        when(pwaRedirectService.resolvePwaRedirectUrl(any())).thenReturn("https://bunnycure-frontend.vercel.app/dashboard");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_MOVED_PERMANENTLY);
        assertThat(response.getHeader("Location")).isEqualTo("https://bunnycure-frontend.vercel.app/dashboard");
        assertThat(response.getHeader("X-Deprecation-Notice")).contains("Monolith view is deprecated");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("No debe redirigir peticiones a endpoints API (/api/**)")
    void doFilterInternal_ShouldNotRedirect_ApiRequests() throws Exception {
        request.setMethod("GET");
        request.setRequestURI("/api/appointments");

        when(pwaRedirectService.isRedirectionEnabled()).thenReturn(true);
        when(pwaRedirectService.isLegacyMonolithPath("/api/appointments")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("No debe redirigir si la propiedad de redirección está deshabilitada")
    void doFilterInternal_ShouldNotRedirect_WhenRedirectionDisabled() throws Exception {
        request.setMethod("GET");
        request.setRequestURI("/dashboard");

        when(pwaRedirectService.isRedirectionEnabled()).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
