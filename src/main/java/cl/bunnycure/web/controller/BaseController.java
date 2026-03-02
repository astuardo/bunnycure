package cl.bunnycure.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ModelAttribute;

public abstract class BaseController {

    @ModelAttribute("activeMenu")
    public String activeMenu(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/customers"))    return "customers";
        if (uri.startsWith("/appointments")) return "appointments";
        if (uri.startsWith("/admin/services")) return "services";
        return "dashboard";
    }
}