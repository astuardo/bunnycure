package cl.bunnycure.web.controller;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.service.AppSettingsService;
import cl.bunnycure.service.BookingRequestService;
import cl.bunnycure.service.PwaRedirectService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * @deprecated En Fase 2, la vista del Dashboard se deprecia a favor del módulo Dashboard de la PWA.
 */
@Deprecated(since = "Phase 2 - PWA Migration", forRemoval = true)
@Controller
@RequiredArgsConstructor
public class DashboardController extends BaseController {

    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository    customerRepository;
    private final BookingRequestService bookingRequestService;
    private final AppSettingsService appSettingsService;
    private final PwaRedirectService pwaRedirectService;

    @GetMapping("/dashboard")
    public Object dashboard(Model model, HttpServletRequest request) {
        if (pwaRedirectService.isRedirectionEnabled()) {
            String redirectUrl = pwaRedirectService.resolvePwaRedirectUrl(request);
            return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                    .header(HttpHeaders.LOCATION, redirectUrl)
                    .header("X-Deprecation-Notice", "Monolith view is deprecated. Redirected to BunnyCure PWA.")
                    .build();
        }
        LocalDate today = LocalDate.now();

        // Fecha formateada en Java para evitar problemas de escape en Thymeleaf
        DateTimeFormatter formatter = DateTimeFormatter
                .ofPattern("EEEE dd 'de' MMMM 'de' yyyy", appSettingsService.getAppJavaLocale());
        String todayFormatted = today.format(formatter);
        todayFormatted = todayFormatted.substring(0, 1).toUpperCase() + todayFormatted.substring(1);

        model.addAttribute("activeMenu",    "dashboard");
        model.addAttribute("today",         today);
        model.addAttribute("todayFormatted", todayFormatted);
        model.addAttribute("todayAppointments",
                appointmentRepository.findByDateWithCustomer(today));
        model.addAttribute("todayCount",
                appointmentRepository.countByStatusAndAppointmentDate(AppointmentStatus.PENDING, today)
                        + appointmentRepository.countByStatusAndAppointmentDate(AppointmentStatus.COMPLETED, today));
        model.addAttribute("pendingCount",
                appointmentRepository.countByStatusAndAppointmentDate(AppointmentStatus.PENDING, today));
        model.addAttribute("completedCount",
                appointmentRepository.countByStatusAndAppointmentDate(AppointmentStatus.COMPLETED, today));
        model.addAttribute("customerCount",
                customerRepository.count());

        // ── Solicitudes de reserva pendientes ──────────────────────────────
        model.addAttribute("pendingRequests",
                bookingRequestService.findPending());
        model.addAttribute("pendingRequestsCount",
                bookingRequestService.countPending());

        return "dashboard";
    }
}