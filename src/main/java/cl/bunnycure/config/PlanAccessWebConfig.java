package cl.bunnycure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class PlanAccessWebConfig implements WebMvcConfigurer {

    private final PlanAccessInterceptor planAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(planAccessInterceptor)
                .addPathPatterns("/api/**")
                // Accesibles aunque la suscripción esté suspendida:
                .excludePathPatterns(
                        "/api/auth/**",                 // iniciar/cerrar sesión, refrescar token
                        "/api/billing/**",              // ver estado de la suscripción y pagar
                        "/api/public/**",               // portal de reservas, tarjetas, registro
                        "/api/admin/tenants/**",        // consola de plataforma
                        "/api/webhooks/**", "/api/webhook/**",
                        "/api/push-subscriptions/public-key",
                        "/api/test/**");
    }
}
