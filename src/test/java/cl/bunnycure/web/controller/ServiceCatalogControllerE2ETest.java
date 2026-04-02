package cl.bunnycure.web.controller;

import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.domain.repository.ServiceCatalogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests E2E para el controlador de Servicios (rubros)
 * Estos tests verifican el flujo completo desde la petición HTTP hasta la base de datos
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("E2E: Gestión de Servicios/Rubros")
class ServiceCatalogControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServiceCatalogRepository repository;

    @Autowired
    private EntityManager entityManager;

    private ServiceCatalog testService;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        // Reiniciar la secuencia para evitar conflictos con datos de Flyway
        entityManager.createNativeQuery("ALTER TABLE service_catalog ALTER COLUMN id RESTART WITH 100").executeUpdate();
        entityManager.flush();
        
        testService = new ServiceCatalog();
        testService.setName("Manicure Básica");
        testService.setDurationMinutes(60);
        testService.setPrice(new BigDecimal("15000"));
        testService.setDescription("Servicio de manicure básico");
        testService.setActive(true);
        testService.setDisplayOrder(1);
        testService = repository.save(testService);
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    // ==================== TESTS DE LISTADO ====================

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Listar servicios - debe mostrar todos los servicios")
    void listServices_ShouldDisplayAllServices() throws Exception {
        mockMvc.perform(get("/admin/services"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/services/list"))
                .andExpect(model().attributeExists("services"))
                .andExpect(model().attribute("services", hasSize(1)))
                .andExpect(content().string(containsString("Manicure Básica")))
                .andExpect(content().string(containsString("15000")));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Listar servicios - debe mostrar lista vacía cuando no hay servicios")
    void listServices_ShouldDisplayEmptyListWhenNoServices() throws Exception {
        repository.deleteAll();

        mockMvc.perform(get("/admin/services"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/services/list"))
                .andExpect(model().attributeExists("services"))
                .andExpect(model().attribute("services", hasSize(0)));
    }

    // ==================== TESTS DE CREACIÓN ====================

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Mostrar formulario de nuevo servicio")
    void newServiceForm_ShouldDisplayForm() throws Exception {
        mockMvc.perform(get("/admin/services/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/services/form"))
                .andExpect(model().attributeExists("service"))
                .andExpect(model().attribute("isNew", true));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Crear servicio válido - debe guardarse correctamente")
    void createService_WithValidData_ShouldSaveSuccessfully() throws Exception {
        mockMvc.perform(post("/admin/services")
                        .with(csrf())
                        .param("name", "Pedicure Spa")
                        .param("durationMinutes", "90")
                        .param("price", "25000")
                        .param("description", "Pedicure completo con spa")
                        .param("displayOrder", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/services"))
                .andExpect(flash().attributeExists("successMsg"));

        // Verificar que se guardó en BD
        var services = repository.findAll();
        assert services.size() == 2;
        assert services.stream().anyMatch(s -> s.getName().equals("Pedicure Spa"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Crear servicio sin nombre - debe rechazar con error")
    void createService_WithoutName_ShouldRejectWithError() throws Exception {
        mockMvc.perform(post("/admin/services")
                        .with(csrf())
                        .param("name", "")
                        .param("durationMinutes", "60")
                        .param("price", "15000"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/services/form"))
                .andExpect(model().attributeHasFieldErrors("service", "name"))
                .andExpect(model().attribute("isNew", true));

        // Verificar que NO se guardó
        assert repository.count() == 1;
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Crear servicio con nombre muy corto - debe rechazar")
    void createService_WithShortName_ShouldRejectWithError() throws Exception {
        mockMvc.perform(post("/admin/services")
                        .with(csrf())
                        .param("name", "AB")
                        .param("durationMinutes", "60")
                        .param("price", "15000"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/services/form"))
                .andExpect(model().attributeHasFieldErrors("service", "name"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Crear servicio con nombre inválido - debe rechazar")
    void createService_WithInvalidCharactersInName_ShouldRejectWithError() throws Exception {
        mockMvc.perform(post("/admin/services")
                        .with(csrf())
                        .param("name", "Servicio<script>alert('xss')</script>")
                        .param("durationMinutes", "60")
                        .param("price", "15000"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/services/form"))
                .andExpect(model().attributeHasFieldErrors("service", "name"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Crear servicio con duración inválida (<15) - debe rechazar")
    void createService_WithInvalidDuration_ShouldRejectWithError() throws Exception {
        mockMvc.perform(post("/admin/services")
                        .with(csrf())
                        .param("name", "Servicio Rápido")
                        .param("durationMinutes", "10")
                        .param("price", "5000"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/services/form"))
                .andExpect(model().attributeHasFieldErrors("service", "durationMinutes"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Crear servicio con duración no múltiplo de 15 - debe rechazar")
    void createService_WithDurationNotMultipleOf15_ShouldRejectWithError() throws Exception {
        mockMvc.perform(post("/admin/services")
                        .with(csrf())
                        .param("name", "Servicio Test")
                        .param("durationMinutes", "50")
                        .param("price", "10000"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/services/form"))
                .andExpect(model().attributeHasFieldErrors("service", "durationMinutes"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Crear servicio con precio negativo - debe rechazar")
    void createService_WithNegativePrice_ShouldRejectWithError() throws Exception {
        mockMvc.perform(post("/admin/services")
                        .with(csrf())
                        .param("name", "Servicio Gratis")
                        .param("durationMinutes", "60")
                        .param("price", "-1000"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/services/form"))
                .andExpect(model().attributeHasFieldErrors("service", "price"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Crear servicio con precio muy bajo por minuto - debe mostrar advertencia")
    void createService_WithLowPricePerMinute_ShouldShowWarning() throws Exception {
        mockMvc.perform(post("/admin/services")
                        .with(csrf())
                        .param("name", "Servicio Económico")
                        .param("durationMinutes", "60")
                        .param("price", "2000"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/services/form"))
                .andExpect(model().attributeHasFieldErrors("service", "price"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Crear servicio con nombre duplicado - debe rechazar")
    void createService_WithDuplicateName_ShouldRejectWithError() throws Exception {
        mockMvc.perform(post("/admin/services")
                        .with(csrf())
                        .param("name", "Manicure Básica")
                        .param("durationMinutes", "60")
                        .param("price", "15000"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/services/form"))
                .andExpect(model().attributeHasFieldErrors("service", "name"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Crear servicio con descripción muy larga - debe rechazar")
    void createService_WithTooLongDescription_ShouldRejectWithError() throws Exception {
        String longDescription = "A".repeat(301);

        mockMvc.perform(post("/admin/services")
                        .with(csrf())
                        .param("name", "Servicio Test")
                        .param("durationMinutes", "60")
                        .param("price", "15000")
                        .param("description", longDescription))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/services/form"))
                .andExpect(model().attributeHasFieldErrors("service", "description"));
    }

    // ==================== TESTS DE EDICIÓN ====================

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Mostrar formulario de edición")
    void editServiceForm_ShouldDisplayFormWithData() throws Exception {
        mockMvc.perform(get("/admin/services/{id}/edit", testService.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/services/form"))
                .andExpect(model().attributeExists("service"))
                .andExpect(model().attribute("isNew", false))
                .andExpect(content().string(containsString("Manicure B&aacute;sica")));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Actualizar servicio válido - debe guardarse correctamente")
    void updateService_WithValidData_ShouldSaveSuccessfully() throws Exception {
        mockMvc.perform(post("/admin/services/{id}/edit", testService.getId())
                        .with(csrf())
                        .param("name", "Manicure Premium")
                        .param("durationMinutes", "90")
                        .param("price", "20000")
                        .param("description", "Manicure premium con diseños")
                        .param("active", "true")
                        .param("displayOrder", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/services"))
                .andExpect(flash().attributeExists("successMsg"));

        // Verificar cambios en BD
        var updated = repository.findById(testService.getId()).orElseThrow();
        assert updated.getName().equals("Manicure Premium");
        assert updated.getDurationMinutes() == 90;
        assert updated.getPrice().compareTo(new BigDecimal("20000")) == 0;
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Actualizar servicio con datos inválidos - debe rechazar")
    void updateService_WithInvalidData_ShouldRejectWithError() throws Exception {
        mockMvc.perform(post("/admin/services/{id}/edit", testService.getId())
                        .with(csrf())
                        .param("name", "")
                        .param("durationMinutes", "0")
                        .param("price", "-1000"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/services/form"))
                .andExpect(model().attributeHasErrors("service"))
                .andExpect(model().attribute("isNew", false));

        // Verificar que NO se actualizó
        var original = repository.findById(testService.getId()).orElseThrow();
        assert original.getName().equals("Manicure Básica");
    }

    // ==================== TESTS DE ACTIVACIÓN/DESACTIVACIÓN ====================

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Cambiar estado de servicio activo a inactivo")
    void toggleService_FromActiveToInactive_ShouldUpdateStatus() throws Exception {
        mockMvc.perform(post("/admin/services/{id}/toggle", testService.getId())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/services"))
                .andExpect(flash().attributeExists("successMsg"));

        // Verificar cambio de estado
        var updated = repository.findById(testService.getId()).orElseThrow();
        assert !updated.getActive();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Cambiar estado de servicio inactivo a activo")
    void toggleService_FromInactiveToActive_ShouldUpdateStatus() throws Exception {
        testService.setActive(false);
        repository.save(testService);

        mockMvc.perform(post("/admin/services/{id}/toggle", testService.getId())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/services"))
                .andExpect(flash().attributeExists("successMsg"));

        // Verificar cambio de estado
        var updated = repository.findById(testService.getId()).orElseThrow();
        assert updated.getActive();
    }

    // ==================== TESTS DE ELIMINACIÓN ====================

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Eliminar servicio sin referencias - debe eliminarse")
    void deleteService_WithoutReferences_ShouldDeleteSuccessfully() throws Exception {
        mockMvc.perform(post("/admin/services/{id}/delete", testService.getId())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/services"))
                .andExpect(flash().attributeExists("successMsg"));

        // Verificar eliminación
        assert !repository.existsById(testService.getId());
    }

    // ==================== TESTS DE SEGURIDAD ====================

    @Test
    @DisplayName("E2E: Acceso sin autenticación - debe redirigir a login")
    void accessWithoutAuth_ShouldRedirectToLogin() throws Exception {
        mockMvc.perform(get("/admin/services"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    @DisplayName("E2E: Acceso con rol USER - debe denegar acceso")
    void accessWithUserRole_ShouldDenyAccess() throws Exception {
        mockMvc.perform(get("/admin/services"))
                .andExpect(status().isForbidden());
    }

    // ==================== TESTS DE VALIDACIÓN CONDICIONAL ====================

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Crear servicio con valores en límites válidos")
    void createService_WithBoundaryValues_ShouldAccept() throws Exception {
        // Duración mínima: 15 min
        mockMvc.perform(post("/admin/services")
                        .with(csrf())
                        .param("name", "Servicio Mínimo")
                        .param("durationMinutes", "15")
                        .param("price", "1000"))
                .andExpect(status().is3xxRedirection());

        // Duración máxima: 480 min (8 horas)
        mockMvc.perform(post("/admin/services")
                        .with(csrf())
                        .param("name", "Servicio Máximo")
                        .param("durationMinutes", "480")
                        .param("price", "50000"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("E2E: Orden de visualización negativo - debe rechazar")
    void createService_WithNegativeDisplayOrder_ShouldRejectWithError() throws Exception {
        mockMvc.perform(post("/admin/services")
                        .with(csrf())
                        .param("name", "Servicio Test")
                        .param("durationMinutes", "60")
                        .param("price", "15000")
                        .param("displayOrder", "-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/services/form"))
                .andExpect(model().attributeHasFieldErrors("service", "displayOrder"));
    }
}
