package cl.bunnycure.service;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.model.User;
import cl.bunnycure.domain.repository.TenantRepository;
import cl.bunnycure.domain.repository.UserRepository;
import cl.bunnycure.exception.ResourceNotFoundException;
import cl.bunnycure.web.dto.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantManagementServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TenantExecutionService tenantExecutionService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query nativeQuery;

    @InjectMocks
    private TenantManagementService tenantManagementService;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Listar salones: retorna lista de salones con conteos")
    void getAllTenants_ReturnsList() {
        Tenant t1 = Tenant.builder().id(1L).name("BunnyCure").slug("bunnycure").active(true).build();
        Tenant t2 = Tenant.builder().id(2L).name("Glamour Salon").slug("glamour").active(true).build();

        when(tenantRepository.findAll(any(Sort.class))).thenReturn(List.of(t1, t2));
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(5L);

        List<TenantResponseDto> result = tenantManagementService.getAllTenants();

        assertEquals(2, result.size());
        assertEquals("BunnyCure", result.get(0).getName());
        assertEquals(5L, result.get(0).getUserCount());
        assertEquals("Glamour Salon", result.get(1).getName());
    }

    @Test
    @DisplayName("Obtener resumen de salones activos: filtra por active=true")
    void getTenantSummaries_ReturnsActive() {
        Tenant t1 = Tenant.builder().id(1L).name("BunnyCure").slug("bunnycure").active(true).build();
        when(tenantRepository.findByActiveTrue()).thenReturn(List.of(t1));

        List<TenantSummaryDto> summaries = tenantManagementService.getTenantSummaries();

        assertEquals(1, summaries.size());
        assertEquals("BunnyCure", summaries.get(0).getName());
        assertTrue(summaries.get(0).isActive());
    }

    @Test
    @DisplayName("Obtener salón por ID: lanza ResourceNotFoundException si no existe")
    void getTenantById_NotFound_ThrowsException() {
        when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tenantManagementService.getTenantById(99L));
    }

    @Test
    @DisplayName("Crear nuevo salón: valida slug único, guarda tenant y aprovisiona admin inicial")
    void createTenant_Success() {
        TenantCreateRequestDto request = TenantCreateRequestDto.builder()
                .name("Glamour Studio")
                .slug("glamour-studio")
                .email("contacto@glamour.cl")
                .initialAdminUsername("admin_glamour")
                .initialAdminPassword("SuperPass123!")
                .initialAdminFullName("Valeria Administradora")
                .build();

        when(tenantRepository.existsBySlugIgnoreCase("glamour-studio")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant t = invocation.getArgument(0);
            t.setId(10L);
            return t;
        });

        // Simular ejecución con executeInTenantTransaction (transacción propia del salón nuevo)
        doAnswer(invocation -> {
            java.util.function.Supplier<?> action = invocation.getArgument(1);
            return action.get();
        }).when(tenantExecutionService).executeInTenantTransaction(eq(10L), any());

        when(passwordEncoder.encode("SuperPass123!")).thenReturn("hashed_password");
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(0L);

        TenantResponseDto response = tenantManagementService.createTenant(request);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("Glamour Studio", response.getName());
        assertEquals("glamour-studio", response.getSlug());

        // Verificar que el admin inicial fue aprovisionado
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User provisioned = userCaptor.getValue();
        assertEquals("admin_glamour", provisioned.getUsername());
        assertEquals("hashed_password", provisioned.getPassword());
        assertEquals("Valeria Administradora", provisioned.getFullName());
        assertEquals(10L, provisioned.getTenantId());
        assertTrue(provisioned.isPasswordChangeRequired());
        assertEquals("ADMIN", provisioned.getRole());
    }

    @Test
    @DisplayName("Crear salón con slug duplicado: lanza IllegalArgumentException")
    void createTenant_DuplicateSlug_ThrowsException() {
        TenantCreateRequestDto request = TenantCreateRequestDto.builder()
                .name("BunnyCure 2")
                .slug("bunnycure")
                .build();

        when(tenantRepository.existsBySlugIgnoreCase("bunnycure")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tenantManagementService.createTenant(request));
        assertTrue(ex.getMessage().contains("Ya existe un salón registrado con el slug"));
    }

    @Test
    @DisplayName("Actualizar salón: modifica campos correctamente")
    void updateTenant_Success() {
        Tenant existing = Tenant.builder()
                .id(2L)
                .name("Antiguo Nombre")
                .slug("salon-dos")
                .active(true)
                .build();

        when(tenantRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(0L);

        TenantUpdateRequestDto request = TenantUpdateRequestDto.builder()
                .name("Nuevo Nombre Salon")
                .phone("+56911223344")
                .planTier("PRO")
                .build();

        TenantResponseDto response = tenantManagementService.updateTenant(2L, request);

        assertEquals("Nuevo Nombre Salon", response.getName());
        assertEquals("+56911223344", response.getPhone());
        assertEquals("PRO", response.getPlanTier());
    }

    @Test
    @DisplayName("Protección de Salón Principal: no permite desactivar tenant 1")
    void toggleTenantStatus_RootTenantProtected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tenantManagementService.toggleTenantStatus(1L, false));
        assertTrue(ex.getMessage().contains("salón principal BunnyCure (ID 1) no puede ser desactivado"));
    }

    @Test
    @DisplayName("Desactivar salón secundario: cambia active a false")
    void toggleTenantStatus_SecondaryTenant_Success() {
        Tenant tenant = Tenant.builder().id(5L).name("Salon VIP").active(true).build();
        when(tenantRepository.findById(5L)).thenReturn(Optional.of(tenant));

        tenantManagementService.toggleTenantStatus(5L, false);

        assertFalse(tenant.isActive());
        verify(tenantRepository).save(tenant);
    }
}
