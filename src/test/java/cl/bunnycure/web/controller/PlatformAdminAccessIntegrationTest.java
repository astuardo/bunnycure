package cl.bunnycure.web.controller;

import cl.bunnycure.config.TestMailConfiguration;
import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.repository.TenantRepository;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La consola de salones (/api/admin/tenants) solo es para la plataforma: SUPER_ADMIN o ADMIN del
 * salón raíz. Una administradora de otro salón (que también tiene rol ADMIN) debe recibir 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestMailConfiguration.class)
@DisplayName("Consola de plataforma: solo SUPER_ADMIN o ADMIN del salón raíz")
class PlatformAdminAccessIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;

    private Long otherTenantId;

    @BeforeEach
    void setUp() {
        otherTenantId = tenantRepository.save(Tenant.builder()
                .name("Salón Glamour").slug("glamour-test-platform").active(true).build()).getId();
    }

    @AfterEach
    void tearDown() {
        tenantRepository.deleteById(otherTenantId);
    }

    @Test
    @DisplayName("ADMIN de otro salón: no puede listar, editar ni desactivar salones (403)")
    void adminOfAnotherSalon_IsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/tenants").with(user("glamour-admin").roles("SALON_ADMIN", "ADMIN"))
                        .header("X-Tenant-ID", otherTenantId))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/admin/tenants/" + otherTenantId).with(user("glamour-admin").roles("SALON_ADMIN", "ADMIN"))
                        .header("X-Tenant-ID", otherTenantId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"planTier\":\"VIP\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/tenants/1/status").with(user("glamour-admin").roles("SALON_ADMIN", "ADMIN"))
                        .header("X-Tenant-ID", otherTenantId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN del salón raíz: accede a la consola")
    void adminOfRootSalon_IsAllowed() throws Exception {
        mockMvc.perform(get("/api/admin/tenants").with(user("owner").roles("SALON_ADMIN", "ADMIN"))
                        .header("X-Tenant-ID", 1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SUPER_ADMIN: accede a la consola")
    void superAdmin_IsAllowed() throws Exception {
        mockMvc.perform(get("/api/admin/tenants").with(user("root").roles("SUPER_ADMIN", "SALON_ADMIN", "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RECEPTIONIST del salón raíz: no accede (403)")
    void receptionistOfRootSalon_IsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/tenants").with(user("recepcion").roles("RECEPTIONIST"))
                        .header("X-Tenant-ID", 1))
                .andExpect(status().isForbidden());
    }
}
