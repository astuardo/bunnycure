package cl.bunnycure.config;

import cl.bunnycure.service.billing.PlanAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Aplica el plan y el estado de la suscripción a la API del salón.
 *
 * 1. Suscripción suspendida: 402 en toda la API autenticada del salón, salvo las rutas
 *    excluidas en {@link PlanAccessWebConfig} (login, suscripción, público, webhooks...),
 *    para que la administradora pueda entrar y regularizar el pago.
 * 2. {@link RequiresPlanFeature} en el controlador o método: 402 si el plan no la incluye.
 *
 * Las excepciones lanzadas aquí las resuelve GlobalExceptionHandler (HTTP 402).
 */
@Component
@RequiredArgsConstructor
public class PlanAccessInterceptor implements HandlerInterceptor {

    private final PlanAccessService planAccessService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        planAccessService.assertSubscriptionAllowsAccess();

        RequiresPlanFeature required = handlerMethod.getMethodAnnotation(RequiresPlanFeature.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequiresPlanFeature.class);
        }
        if (required != null) {
            planAccessService.assertFeature(required.value());
        }
        return true;
    }
}
