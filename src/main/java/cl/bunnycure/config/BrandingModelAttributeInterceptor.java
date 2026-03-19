package cl.bunnycure.config;

import cl.bunnycure.service.AppSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Interceptor que inyecta variables de branding y configuración global
 * en todos los modelos de Thymeleaf.
 *
 * Fase 1: Parametrización de Identidad & Branding
 * Documentación: docs-dev/FASE_1_BRANDING.md
 */
@Component
@RequiredArgsConstructor
public class BrandingModelAttributeInterceptor implements HandlerInterceptor {

    private final AppSettingsService settingsService;

    @Override
    public void postHandle(HttpServletRequest request,
                          HttpServletResponse response,
                          Object handler,
                          ModelAndView modelAndView) throws Exception {

        if (modelAndView == null) {
            return;
        }

        // ── Variables de Branding ────────────────────────────────────────────
        modelAndView.addObject("appName", settingsService.getAppName());
        modelAndView.addObject("appSlogan", settingsService.getAppSlogan());
        modelAndView.addObject("appEmail", settingsService.getAppEmail());
        modelAndView.addObject("appLogoUrl", settingsService.getAppLogoUrl());
        modelAndView.addObject("appPrimaryColor", settingsService.getAppPrimaryColor());
        modelAndView.addObject("appSecondaryColor", settingsService.getAppSecondaryColor());
        modelAndView.addObject("appTimezone", settingsService.getAppTimezone());
        modelAndView.addObject("appLocale", settingsService.getAppLocale());
        modelAndView.addObject("appCurrency", settingsService.getAppCurrency());
        modelAndView.addObject("appServiceTip", settingsService.getAppServiceTip());
    }
}
