package cl.bunnycure.service;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.config.TenantContextFilter;
import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.repository.TenantRepository;
import cl.bunnycure.web.controller.TenantPublicApiController;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.TenantInfoDto;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantResolutionTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private AppSettingsService appSettingsService;

    @Mock
    private FilterChain filterChain;

    private TenantContextFilter tenantContextFilter;
    private TenantPublicApiController tenantPublicApiController;

    @BeforeEach
    void setUp() {
        tenantContextFilter = new TenantContextFilter(tenantRepository);
        tenantPublicApiController = new TenantPublicApiController(tenantRepository, appSettingsService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void filter_ResolvesTenantByCustomDomainHeader() throws Exception {
        // Arrange
        Tenant glamTenant = Tenant.builder()
                .id(2L)
                .name("Glamour Nails Studio")
                .slug("glamour")
                .customDomain("reservas.glamournails.cl")
                .primaryColor("#e91e63")
                .active(true)
                .build();

        when(tenantRepository.findByCustomDomainIgnoreCase("reservas.glamournails.cl"))
                .thenReturn(Optional.of(glamTenant));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Salon-Domain", "reservas.glamournails.cl");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(inv -> {
            // Assert that during filter execution, TenantContext is populated
            assertEquals(2L, TenantContext.getCurrentTenantId());
            return null;
        }).when(filterChain).doFilter(request, response);

        // Act
        tenantContextFilter.doFilter(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        // After filter completes, context should be cleared
        assertEquals(1L, TenantContext.getCurrentTenantId());
    }

    @Test
    void filter_ResolvesTenantByHostHeader() throws Exception {
        // Arrange
        Tenant bunnyTenant = Tenant.builder()
                .id(1L)
                .name("BunnyCure Studio")
                .slug("bunnycure")
                .customDomain("app.bunnycure.cl")
                .build();

        when(tenantRepository.findByCustomDomainIgnoreCase("app.bunnycure.cl"))
                .thenReturn(Optional.of(bunnyTenant));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Host", "app.bunnycure.cl:443");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(inv -> {
            assertEquals(1L, TenantContext.getCurrentTenantId());
            return null;
        }).when(filterChain).doFilter(request, response);

        // Act
        tenantContextFilter.doFilter(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void getTenantInfo_ReturnsTenantDetailsForFrontendBranding() {
        // Arrange
        Tenant tenant = Tenant.builder()
                .id(1L)
                .name("BunnyCure Studio")
                .slug("bunnycure")
                .customDomain("app.bunnycure.cl")
                .primaryColor("#d48b70")
                .phone("+56983692046")
                .active(true)
                .planTier("PRO")
                .build();

        when(tenantRepository.findByCustomDomainIgnoreCase("app.bunnycure.cl"))
                .thenReturn(Optional.of(tenant));

        // Act
        ResponseEntity<ApiResponse<TenantInfoDto>> response = tenantPublicApiController.getTenantInfo("app.bunnycure.cl", null);

        // Assert
        assertNotNull(response.getBody());
        TenantInfoDto dto = response.getBody().getData();
        assertEquals(1L, dto.getId());
        assertEquals("BunnyCure Studio", dto.getName());
        assertEquals("bunnycure", dto.getSlug());
        assertEquals("#d48b70", dto.getPrimaryColor());
        assertEquals("PRO", dto.getPlanTier());
    }

    @Test
    void getTenantInfo_WithFallbacks_ReturnsEnrichedIdentity() {
        // Arrange
        Tenant minimalTenant = Tenant.builder()
                .id(2L)
                .name("Glamour Salon")
                .slug("glamour")
                .primaryColor(null)
                .active(true)
                .build();

        when(tenantRepository.findBySlugIgnoreCase("glamour"))
                .thenReturn(Optional.of(minimalTenant));
        when(appSettingsService.getAppPrimaryColor()).thenReturn("#C9897A");
        when(appSettingsService.getAppSecondaryColor()).thenReturn("#6B4C38");
        when(appSettingsService.getAppSlogan()).thenReturn("Arte en tus manos ✨");
        when(appSettingsService.getMorningBlock()).thenReturn("09:00 – 13:00");
        when(appSettingsService.isBookingEnabled()).thenReturn(true);

        // Act
        ResponseEntity<ApiResponse<TenantInfoDto>> response = tenantPublicApiController.getTenantInfo(null, "glamour");

        // Assert
        assertNotNull(response.getBody());
        TenantInfoDto dto = response.getBody().getData();
        assertEquals(2L, dto.getId());
        assertEquals("Glamour Salon", dto.getName());
        assertEquals("glamour", dto.getSlug());
        assertEquals("#C9897A", dto.getPrimaryColor());
        assertEquals("#6B4C38", dto.getSecondaryColor());
        assertEquals("Arte en tus manos ✨", dto.getSlogan());
        assertEquals("09:00 – 13:00", dto.getMorningBlock());
        org.junit.jupiter.api.Assertions.assertTrue(dto.isBookingEnabled());
    }
}
