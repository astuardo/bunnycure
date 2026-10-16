package cl.bunnycure.config;

import cl.bunnycure.domain.enums.PlanFeature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un controlador (o método) como parte de una funcionalidad de pago.
 * {@link PlanAccessInterceptor} responde 402 si el plan del salón no la incluye.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPlanFeature {
    PlanFeature value();
}
