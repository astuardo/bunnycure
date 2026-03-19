package cl.bunnycure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración de Web MVC para registrar interceptores.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final PasswordChangeInterceptor passwordChangeInterceptor;
    private final BrandingModelAttributeInterceptor brandingModelAttributeInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // ── Branding global ─────────────────────────────────────────────────
        registry.addInterceptor(brandingModelAttributeInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/**",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/webjars/**",
                        "/favicon.ico",
                        "/error"
                );

        // ── Validación de password ──────────────────────────────────────────
        registry.addInterceptor(passwordChangeInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login",
                        "/login/**",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/webjars/**",
                        "/favicon.ico",
                        "/error",
                        "/",
                        "/reservar/**",
                        "/api/**"
                );
    }
}
