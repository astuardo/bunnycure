package cl.bunnycure.service;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantExecutionServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    private TenantExecutionService tenantExecutionService;

    @BeforeEach
    void setUp() {
        tenantExecutionService = new TenantExecutionService(tenantRepository);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void executeForAllActiveTenants_shouldIterateAndSetContextForEachTenant() {
        Tenant t1 = Tenant.builder().id(1L).slug("salon-a").name("Salón A").active(true).build();
        Tenant t2 = Tenant.builder().id(2L).slug("salon-b").name("Salón B").active(true).build();

        when(tenantRepository.findByActiveTrue()).thenReturn(List.of(t1, t2));

        List<Long> seenTenantIdsInAction = new ArrayList<>();

        tenantExecutionService.executeForAllActiveTenants("Test Task", tenant -> {
            seenTenantIdsInAction.add(TenantContext.getCurrentTenantId());
        });

        assertEquals(List.of(1L, 2L), seenTenantIdsInAction);
        assertFalse(TenantContext.hasExplicitTenant());
        assertEquals(TenantContext.DEFAULT_TENANT_ID, TenantContext.getCurrentTenantId());
    }

    @Test
    void executeForAllActiveTenants_whenOneFails_shouldContinueWithNextTenant() {
        Tenant t1 = Tenant.builder().id(1L).slug("salon-a").name("Salón A").active(true).build();
        Tenant t2 = Tenant.builder().id(2L).slug("salon-b").name("Salón B").active(true).build();

        when(tenantRepository.findByActiveTrue()).thenReturn(List.of(t1, t2));

        List<Long> processedTenants = new ArrayList<>();

        tenantExecutionService.executeForAllActiveTenants("Failing Task", tenant -> {
            if (tenant.getId() == 1L) {
                throw new RuntimeException("Simulated error for salon 1");
            }
            processedTenants.add(tenant.getId());
        });

        assertEquals(List.of(2L), processedTenants);
        assertFalse(TenantContext.hasExplicitTenant());
        assertEquals(TenantContext.DEFAULT_TENANT_ID, TenantContext.getCurrentTenantId());
    }

    @Test
    void executeInTenantContext_runnable_shouldSetAndClearContext() {
        Long[] seenId = new Long[1];

        tenantExecutionService.executeInTenantContext(42L, () -> {
            seenId[0] = TenantContext.getCurrentTenantId();
        });

        assertEquals(42L, seenId[0]);
        assertFalse(TenantContext.hasExplicitTenant());
        assertEquals(TenantContext.DEFAULT_TENANT_ID, TenantContext.getCurrentTenantId());
    }

    @Test
    void executeInTenantContext_runnable_shouldClearContextOnException() {
        assertThrows(RuntimeException.class, () ->
                tenantExecutionService.executeInTenantContext(99L, (Runnable) () -> {
                    throw new RuntimeException("Failure");
                })
        );

        assertFalse(TenantContext.hasExplicitTenant());
        assertEquals(TenantContext.DEFAULT_TENANT_ID, TenantContext.getCurrentTenantId());
    }

    @Test
    void executeInTenantContext_supplier_shouldReturnResultAndClearContext() {
        String result = tenantExecutionService.executeInTenantContext(77L, () -> {
            assertEquals(77L, TenantContext.getCurrentTenantId());
            return "SUCCESS";
        });

        assertEquals("SUCCESS", result);
        assertFalse(TenantContext.hasExplicitTenant());
        assertEquals(TenantContext.DEFAULT_TENANT_ID, TenantContext.getCurrentTenantId());
    }
}
