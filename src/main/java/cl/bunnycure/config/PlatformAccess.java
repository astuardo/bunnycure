package cl.bunnycure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Autorización de la consola de plataforma (gestión de salones, planes y cobros).
 *
 * Pueden operarla: SUPER_ADMIN, o una ADMIN del salón raíz (el dueño de la plataforma).
 * Las administradoras de cualquier otro salón también tienen el rol ADMIN, por lo que el
 * rol por sí solo NO basta: se exige además que el tenant del token sea el raíz.
 *
 * Uso: {@code @PreAuthorize("@platformAccess.isPlatformAdmin(authentication)")}
 */
@Component("platformAccess")
public class PlatformAccess {

    @Value("${bunnycure.platform.root-tenant-id:1}")
    private Long rootTenantId;

    public boolean isPlatformAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Set<String> roles = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        if (roles.contains("ROLE_SUPER_ADMIN")) {
            return true;
        }
        // TenantContextFilter fija explícitamente el tenant del JWT verificado (y rechaza con 403
        // a quien intente suplantar otro salón), por eso se exige el tenant explícito, no el de fallback.
        return roles.contains("ROLE_ADMIN") && rootTenantId.equals(TenantContext.getExplicitTenantId());
    }
}
