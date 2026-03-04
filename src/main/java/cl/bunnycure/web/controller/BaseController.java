package cl.bunnycure.web.controller;

import cl.bunnycure.domain.enums.BookingRequestStatus;
import cl.bunnycure.domain.repository.BookingRequestRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;

public abstract class BaseController {

    @Autowired
    private BookingRequestRepository bookingRequestRepository;

    @ModelAttribute("activeMenu")
    public String activeMenu(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/customers"))                  return "customers";
        if (uri.startsWith("/appointments"))               return "appointments";
        if (uri.startsWith("/admin/booking-requests"))     return "booking-requests";
        if (uri.startsWith("/admin/services"))             return "services";
        if (uri.startsWith("/admin/settings"))             return "settings";
        return "dashboard";
    }

    /**
     * Expone el conteo de solicitudes pendientes a todas las vistas del admin.
     * Se usa en base.html para mostrar el badge en el sidebar.
     */
    @ModelAttribute("pendingBookingCount")
    public long pendingBookingCount() {
        return bookingRequestRepository.countByStatus(BookingRequestStatus.PENDING);
    }
}