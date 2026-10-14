package cl.bunnycure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;

@DisplayName("CorsConfig: Configuración y evaluación de orígenes")
class CorsConfigTest {

    private CorsConfig corsConfig;

    @BeforeEach
    void setUp() {
        corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(corsConfig, "allowedOriginsConfig", "http://localhost:5173,https://bunnycure-frontend.vercel.app");
        ReflectionTestUtils.setField(corsConfig, "allowedOriginPatternsConfig", "https://*.vercel.app,https://*.bunnycure.cl");
        ReflectionTestUtils.setField(corsConfig, "allowedMethodsConfig", "GET,POST,PUT,DELETE,PATCH,OPTIONS,HEAD");
        ReflectionTestUtils.setField(corsConfig, "allowedHeadersConfig", "Authorization,Content-Type,Accept,Origin,X-XSRF-TOKEN,X-CSRF-TOKEN");
        ReflectionTestUtils.setField(corsConfig, "exposedHeadersConfig", "Authorization,Location,X-Deprecation-Notice");
        ReflectionTestUtils.setField(corsConfig, "allowCredentials", true);
        ReflectionTestUtils.setField(corsConfig, "maxAge", 3600L);
    }

    @Test
    @DisplayName("Debe registrar configuración CORS para todas las rutas /**")
    void corsConfigurationSource_ShouldCoverAllRoutes() {
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();

        MockHttpServletRequest apiRequest = new MockHttpServletRequest("GET", "/api/appointments");
        CorsConfiguration apiConfig = source.getCorsConfiguration(apiRequest);
        assertThat(apiConfig).isNotNull();

        MockHttpServletRequest rootRequest = new MockHttpServletRequest("GET", "/");
        CorsConfiguration rootConfig = source.getCorsConfiguration(rootRequest);
        assertThat(rootConfig).isNotNull();

        MockHttpServletRequest loginRequest = new MockHttpServletRequest("POST", "/login");
        CorsConfiguration loginConfig = source.getCorsConfiguration(loginRequest);
        assertThat(loginConfig).isNotNull();
    }

    @Test
    @DisplayName("Debe incluir métodos, headers expuestos y soporte de credenciales")
    void corsConfigurationSource_ShouldHaveExpectedRules() {
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertThat(config).isNotNull();
        assertThat(config.getAllowCredentials()).isTrue();
        assertThat(config.getAllowedMethods()).contains("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(config.getAllowedOriginPatterns()).contains("https://*.vercel.app", "https://*.bunnycure.cl");
        assertThat(config.getExposedHeaders()).contains("Location", "X-Deprecation-Notice");
    }

    @Test
    @DisplayName("Debe permitir headers personalizados de multi-tenancy (x-tenant-slug, x-tenant-id)")
    void corsConfigurationSource_ShouldAllowTenantHeaders() {
        CorsConfig customConfig = new CorsConfig();
        ReflectionTestUtils.setField(customConfig, "allowedOriginsConfig", "https://app.bunnycure.cl");
        ReflectionTestUtils.setField(customConfig, "allowedOriginPatternsConfig", "https://*.bunnycure.cl");
        ReflectionTestUtils.setField(customConfig, "allowedMethodsConfig", "GET,POST,PUT,DELETE,PATCH,OPTIONS,HEAD");
        ReflectionTestUtils.setField(customConfig, "allowedHeadersConfig", "Authorization,Content-Type,Accept,Origin,X-Tenant-Slug,x-tenant-slug,X-Tenant-ID,x-tenant-id,*");
        ReflectionTestUtils.setField(customConfig, "exposedHeadersConfig", "Authorization,Location,X-Tenant-ID,X-Tenant-Slug");
        ReflectionTestUtils.setField(customConfig, "allowCredentials", true);
        ReflectionTestUtils.setField(customConfig, "maxAge", 3600L);

        CorsConfigurationSource source = customConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        request.addHeader("Origin", "https://app.bunnycure.cl");
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "x-tenant-slug,x-tenant-id,authorization");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertThat(config).isNotNull();
        assertThat(config.checkHeaders(List.of("x-tenant-slug", "x-tenant-id", "authorization"))).isNotNull();
    }
}
