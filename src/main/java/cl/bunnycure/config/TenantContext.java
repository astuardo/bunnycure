package cl.bunnycure.config;

import lombok.extern.slf4j.Slf4j;

/**
 * Almacena el ID del salón / tenant activo en el ThreadLocal de la petición HTTP actual.
 * Proporciona fallback transparente al salón principal por defecto (ID=1).
 */
@Slf4j
public class TenantContext {

    public static final Long DEFAULT_TENANT_ID = 1L;
    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    public static void setCurrentTenantId(Long tenantId) {
        if (tenantId != null) {
            CURRENT_TENANT.set(tenantId);
        } else {
            CURRENT_TENANT.set(DEFAULT_TENANT_ID);
        }
    }

    public static Long getCurrentTenantId() {
        Long tenantId = CURRENT_TENANT.get();
        return tenantId != null ? tenantId : DEFAULT_TENANT_ID;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
