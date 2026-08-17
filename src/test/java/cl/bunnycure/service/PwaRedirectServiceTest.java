package cl.bunnycure.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PwaRedirectService: Mapeo y resolución de URLs para PWA")
class PwaRedirectServiceTest {

    private PwaRedirectService service;

    @BeforeEach
    void setUp() {
        service = new PwaRedirectService();
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://bunnycure-frontend.vercel.app");
        ReflectionTestUtils.setField(service, "redirectionEnabled", true);
    }

    @Test
    @DisplayName("Debe identificar correctamente las rutas del monolito que deben redirigirse")
    void isLegacyMonolithPath_ShouldIdentifyMonolithRoutes() {
        assertThat(service.isLegacyMonolithPath("/")).isTrue();
        assertThat(service.isLegacyMonolithPath("/reservar")).isTrue();
        assertThat(service.isLegacyMonolithPath("/reservar/")).isTrue();
        assertThat(service.isLegacyMonolithPath("/login")).isTrue();
        assertThat(service.isLegacyMonolithPath("/dashboard")).isTrue();
        assertThat(service.isLegacyMonolithPath("/appointments")).isTrue();
        assertThat(service.isLegacyMonolithPath("/appointments/123")).isTrue();
        assertThat(service.isLegacyMonolithPath("/customers")).isTrue();
        assertThat(service.isLegacyMonolithPath("/customers/abc-def")).isTrue();
        assertThat(service.isLegacyMonolithPath("/admin/services")).isTrue();
        assertThat(service.isLegacyMonolithPath("/admin/settings")).isTrue();
        assertThat(service.isLegacyMonolithPath("/admin/booking-requests")).isTrue();
        assertThat(service.isLegacyMonolithPath("/forgot-password")).isTrue();
        assertThat(service.isLegacyMonolithPath("/reset-password")).isTrue();
    }

    @Test
    @DisplayName("Debe excluir APIs REST, Google Wallet passes y recursos estáticos de la redirección")
    void isLegacyMonolithPath_ShouldExcludeApiAndStaticAssets() {
        assertThat(service.isLegacyMonolithPath("/api/appointments")).isFalse();
        assertThat(service.isLegacyMonolithPath("/api/customers/lookup")).isFalse();
        assertThat(service.isLegacyMonolithPath("/api/auth/login")).isFalse();
        assertThat(service.isLegacyMonolithPath("/w/abc12345")).isFalse();
        assertThat(service.isLegacyMonolithPath("/css/style.css")).isFalse();
        assertThat(service.isLegacyMonolithPath("/js/service-validation.js")).isFalse();
        assertThat(service.isLegacyMonolithPath("/assets/wallet/hero_1.svg")).isFalse();
        assertThat(service.isLegacyMonolithPath("/favicon.ico")).isFalse();
        assertThat(service.isLegacyMonolithPath("/.well-known/appspecific/com.chrome.devtools.json")).isFalse();
        assertThat(service.isLegacyMonolithPath("/swagger-ui/index.html")).isFalse();
        assertThat(service.isLegacyMonolithPath("/v3/api-docs")).isFalse();
        assertThat(service.isLegacyMonolithPath("/h2-console")).isFalse();
    }

    @Test
    @DisplayName("Debe resolver la raíz según el host header")
    void resolvePwaRedirectUrl_RootRoutingByHost() {
        // admin host
        String adminUrl = service.resolvePwaRedirectUrl("/", null, "admin.bunnycure.cl");
        assertThat(adminUrl).isEqualTo("https://bunnycure-frontend.vercel.app/dashboard");

        // reservar host
        String reservarUrl = service.resolvePwaRedirectUrl("/", null, "reservar.bunnycure.cl");
        assertThat(reservarUrl).isEqualTo("https://bunnycure-frontend.vercel.app/reservar");

        // generic host
        String genericUrl = service.resolvePwaRedirectUrl("/", null, "bunnycure.cl");
        assertThat(genericUrl).isEqualTo("https://bunnycure-frontend.vercel.app/");
    }

    @Test
    @DisplayName("Debe preservar query strings en las redirecciones")
    void resolvePwaRedirectUrl_ShouldPreserveQueryStrings() {
        String resetUrl = service.resolvePwaRedirectUrl("/reset-password", "token=secret-123", "bunnycure.cl");
        assertThat(resetUrl).isEqualTo("https://bunnycure-frontend.vercel.app/reset-password?token=secret-123");

        String customerSearch = service.resolvePwaRedirectUrl("/customers", "search=Camila", "bunnycure.cl");
        assertThat(customerSearch).isEqualTo("https://bunnycure-frontend.vercel.app/customers?search=Camila");

        String appointmentDate = service.resolvePwaRedirectUrl("/appointments", "view=day&date=2026-08-20", "bunnycure.cl");
        assertThat(appointmentDate).isEqualTo("https://bunnycure-frontend.vercel.app/appointments?view=day&date=2026-08-20");
    }

    @Test
    @DisplayName("Debe normalizar la URL base del frontend sin trailing slash redundante")
    void getFrontendBaseUrl_ShouldNormalize() {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://bunnycure-frontend.vercel.app/  ");
        assertThat(service.getFrontendBaseUrl()).isEqualTo("https://bunnycure-frontend.vercel.app");

        ReflectionTestUtils.setField(service, "frontendBaseUrl", "");
        assertThat(service.getFrontendBaseUrl()).isEqualTo("https://bunnycure-frontend.vercel.app");
    }
}
