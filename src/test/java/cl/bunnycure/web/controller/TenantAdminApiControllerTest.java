package cl.bunnycure.web.controller;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.exception.GlobalExceptionHandler;
import cl.bunnycure.service.TenantManagementService;
import cl.bunnycure.web.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TenantAdminApiControllerTest {

    @Mock
    private TenantManagementService tenantManagementService;

    @InjectMocks
    private TenantAdminApiController tenantAdminApiController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Authentication superAdminAuth;
    private Authentication rootAdminAuth;
    private Authentication tenant2AdminAuth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(tenantAdminApiController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        superAdminAuth = new UsernamePasswordAuthenticationToken(
                "superadmin", "pass", List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));

        rootAdminAuth = new UsernamePasswordAuthenticationToken(
                "admin", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        tenant2AdminAuth = new UsernamePasswordAuthenticationToken(
                "admin2", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/admin/tenants - SuperAdmin puede listar todos los salones")
    void getAllTenants_SuperAdmin_Success() throws Exception {
        TenantResponseDto t1 = TenantResponseDto.builder()
                .id(1L)
                .name("BunnyCure")
                .slug("bunnycure")
                .active(true)
                .build();

        when(tenantManagementService.getAllTenants()).thenReturn(List.of(t1));

        mockMvc.perform(get("/api/admin/tenants")
                        .principal(superAdminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("BunnyCure"));
    }

    @Test
    @DisplayName("GET /api/admin/tenants - Admin de tenant 2 recibe 403 Forbidden")
    void getAllTenants_Tenant2Admin_Forbidden() throws Exception {
        TenantContext.setCurrentTenantId(2L);

        mockMvc.perform(get("/api/admin/tenants")
                        .principal(tenant2AdminAuth))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/admin/tenants - Crear nuevo salón exitosamente")
    void createTenant_Success() throws Exception {
        TenantCreateRequestDto request = TenantCreateRequestDto.builder()
                .name("Glamour Salon")
                .slug("glamour-salon")
                .primaryColor("#ff66aa")
                .planTier("PRO")
                .build();

        TenantResponseDto response = TenantResponseDto.builder()
                .id(2L)
                .name("Glamour Salon")
                .slug("glamour-salon")
                .primaryColor("#ff66aa")
                .planTier("PRO")
                .active(true)
                .build();

        when(tenantManagementService.createTenant(any(TenantCreateRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/admin/tenants")
                        .principal(superAdminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.slug").value("glamour-salon"));
    }

    @Test
    @DisplayName("PUT /api/admin/tenants/{id} - Actualizar datos de un salón")
    void updateTenant_Success() throws Exception {
        TenantUpdateRequestDto request = TenantUpdateRequestDto.builder()
                .name("BunnyCure VIP")
                .phone("+56999887766")
                .build();

        TenantResponseDto response = TenantResponseDto.builder()
                .id(1L)
                .name("BunnyCure VIP")
                .phone("+56999887766")
                .active(true)
                .build();

        when(tenantManagementService.updateTenant(eq(1L), any(TenantUpdateRequestDto.class))).thenReturn(response);

        mockMvc.perform(put("/api/admin/tenants/1")
                        .principal(rootAdminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("BunnyCure VIP"));
    }

    @Test
    @DisplayName("PATCH /api/admin/tenants/{id}/status - Conmutar estado de salón")
    void toggleTenantStatus_Success() throws Exception {
        doNothing().when(tenantManagementService).toggleTenantStatus(2L, false);

        mockMvc.perform(patch("/api/admin/tenants/2/status")
                        .principal(superAdminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(tenantManagementService).toggleTenantStatus(2L, false);
    }
}
