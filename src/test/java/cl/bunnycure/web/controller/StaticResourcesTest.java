package cl.bunnycure.web.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test para verificar que los recursos estáticos JavaScript se sirven correctamente
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Static Resources: JavaScript Validation")
class StaticResourcesTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Debe servir el archivo service-validation.js")
    void serveServiceValidationJs_ShouldReturn200() throws Exception {
        mockMvc.perform(get("/js/service-validation.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/javascript"))
                .andExpect(content().string(containsString("client-side")))
                .andExpect(content().string(containsString("validateDuration")))
                .andExpect(content().string(containsString("updateCharCounter")));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("El acceso a vistas legadas como /admin/services/new debe redirigir 301 a la PWA")
    void serviceForm_ShouldRedirectToPwa() throws Exception {
        mockMvc.perform(get("/admin/services/new"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", containsString("/admin/services")))
                .andExpect(header().string("X-Deprecation-Notice", containsString("Monolith view is deprecated")));
    }
}
