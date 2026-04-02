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
                .andExpect(content().string(containsString("Validación client-side")))
                .andExpect(content().string(containsString("validateDuration")))
                .andExpect(content().string(containsString("updateCharCounter")));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("El formulario de servicios debe cargar el script de validación")
    void serviceForm_ShouldLoadValidationScript() throws Exception {
        mockMvc.perform(get("/admin/services/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("service-validation.js")))
                .andExpect(content().string(containsString("descriptionCharCount")));
    }
}
