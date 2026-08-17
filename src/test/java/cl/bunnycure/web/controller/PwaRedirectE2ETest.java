package cl.bunnycure.web.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("E2E: Redirecciones 301 hacia la PWA (Fase 2)")
class PwaRedirectE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "GET {0} debe retornar HTTP 301 con Location a la PWA")
    @ValueSource(strings = {
            "/",
            "/reservar",
            "/login",
            "/dashboard",
            "/appointments",
            "/customers",
            "/admin/services",
            "/admin/users",
            "/admin/settings",
            "/admin/reminders",
            "/admin/booking-requests",
            "/forgot-password",
            "/reset-password"
    })
    void legacyRoutes_ShouldRedirect301ToPwa(String route) throws Exception {
        mockMvc.perform(get(route))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(header().string("X-Deprecation-Notice", containsString("Monolith view is deprecated")));
    }

    @Test
    @DisplayName("Host admin.bunnycure.cl en la raíz debe redirigir a /dashboard")
    void adminHost_ShouldRedirectToDashboard() throws Exception {
        mockMvc.perform(get("/").header("Host", "admin.bunnycure.cl"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/dashboard")));
    }

    @Test
    @DisplayName("Host reservar.bunnycure.cl en la raíz debe redirigir a /reservar")
    void reservarHost_ShouldRedirectToReservar() throws Exception {
        mockMvc.perform(get("/").header("Host", "reservar.bunnycure.cl"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/reservar")));
    }

    @Test
    @DisplayName("Rutas con parámetros deben conservar el query string en el Location")
    void queryParams_ShouldBePreservedInLocationHeader() throws Exception {
        mockMvc.perform(get("/reset-password").param("token", "xyz-token-123"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("token=xyz-token-123")));
    }
}
