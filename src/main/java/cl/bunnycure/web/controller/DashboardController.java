package cl.bunnycure.web.controller;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.service.BookingRequestService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Controller
public class DashboardController extends BaseController {

    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository    customerRepository;
    private final BookingRequestService bookingRequestService;

    public DashboardController(AppointmentRepository appointmentRepository,
                               CustomerRepository customerRepository,
                               BookingRequestService bookingRequestService) {
        this.appointmentRepository = appointmentRepository;
        this.customerRepository    = customerRepository;
        this.bookingRequestService = bookingRequestService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        LocalDate today = LocalDate.now();

        // Fecha formateada en Java para evitar problemas de escape en Thymeleaf
        DateTimeFormatter formatter = DateTimeFormatter
                .ofPattern("EEEE dd 'de' MMMM 'de' yyyy", new Locale("es", "CL"));
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