package cl.bunnycure.web.controller;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.repository.TenantRepository;
import cl.bunnycure.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantHttpAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TenantRepository tenantRepository;

    private Tenant defaultTenant;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        defaultTenant = tenantRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default tenant 1 not found"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void publicRequest_withNoTenantHeaders_shouldSucceedWithDefaultTenant() throws Exception {
        mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk());

        assertFalse(TenantContext.hasExplicitTenant());
        assertEquals(TenantContext.DEFAULT_TENANT_ID, TenantContext.getCurrentTenantId());
    }

    @Test
    void publicRequest_withTenantSlugHeader_shouldResolveTenantContext() throws Exception {
        mockMvc.perform(get("/api/services")
                        .header("X-Tenant-Slug", defaultTenant.getSlug()))
                .andExpect(status().isOk());

        assertFalse(TenantContext.hasExplicitTenant());
    }

    @Test
    void publicRequest_withTenantIdHeader_shouldResolveTenantContext() throws Exception {
        mockMvc.perform(get("/api/services")
                        .header("X-Tenant-ID", defaultTenant.getId().toString()))
                .andExpect(status().isOk());

        assertFalse(TenantContext.hasExplicitTenant());
    }

    @Test
    void publicRequest_withSubdomainHost_shouldResolveTenantContext() throws Exception {
        mockMvc.perform(get("/api/services")
                        .header("Host", defaultTenant.getSlug() + ".bunnycure.cl"))
                .andExpect(status().isOk());

        assertFalse(TenantContext.hasExplicitTenant());
    }

    @Test
    void authenticatedRequest_withJwtBearingTenantClaims_shouldResolveTenantContext() throws Exception {
        UserDetails adminDetails = new User("admin", "$2a$10$dummy", Collections.emptyList());
        String token = jwtService.generateToken(adminDetails, Map.of(
                "tenantId", defaultTenant.getId(),
                "tenantSlug", defaultTenant.getSlug()
        ));

        mockMvc.perform(get("/api/services")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertFalse(TenantContext.hasExplicitTenant());
    }
}
