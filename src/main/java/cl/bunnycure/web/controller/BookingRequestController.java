package cl.bunnycure.web.controller;

import cl.bunnycure.service.BookingRequestService;
import cl.bunnycure.service.ServiceCatalogService;
import cl.bunnycure.service.AppSettingsService;
import cl.bunnycure.service.WhatsAppHandoffService;
import cl.bunnycure.web.dto.BookingApprovalDto;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin/booking-requests")
public class BookingRequestController extends BaseController {

    private final BookingRequestService bookingRequestService;
    private final ServiceCatalogService serviceCatalogService;
    private final WhatsAppHandoffService whatsAppHandoffService;
    private final AppSettingsService appSettingsService;

    public BookingRequestController(BookingRequestService bookingRequestService,
                                    ServiceCatalogService serviceCatalogService,
                                    WhatsAppHandoffService whatsAppHandoffService,
                                    AppSettingsService appSettingsService) {
        this.bookingRequestService = bookingRequestService;
        this.serviceCatalogService = serviceCatalogService;
        this.whatsAppHandoffService = whatsAppHandoffService;
        this.appSettingsService = appSettingsService;
    }

    // ── Lista de solicitudes ─────────────────────────────────────────────────
    @GetMapping
    public String list(Model model) {
        model.addAttribute("pending",  bookingRequestService.findPending());
        model.addAttribute("all",      bookingRequestService.findAll());
        model.addAttribute("pendingCount", bookingRequestService.countPending());
        return "admin/booking-requests/list";
    }

    // ── Detalle / formulario de aprobación ───────────────────────────────────
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        var request = bookingRequestService.findById(id);
        model.addAttribute("request",  request);
        var approval = new BookingApprovalDto();
        approval.setAppointmentDate(request.getPreferredDate());
        model.addAttribute("approval", approval);
        model.addAttribute("minAppointmentDate", LocalDate.now());
        model.addAttribute("services", serviceCatalogService.findAll()
                .stream().filter(s -> s.isActive()).toList());
        addHandoffModelAttributes(model, request);
        return "admin/booking-requests/detail";
    }

    // ── Aprobar ──────────────────────────────────────────────────────────────
    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id,
                          @Valid @ModelAttribute("approval") BookingApprovalDto approval,
                          BindingResult result,
                          Model model,
                          RedirectAttributes flash) {
        if (result.hasErrors()) {
            var request = bookingRequestService.findById(id);
            model.addAttribute("request",  request);
            model.addAttribute("minAppointmentDate", LocalDate.now());
            model.addAttribute("services", serviceCatalogService.findAll()
                    .stream().filter(s -> s.isActive()).toList());
            addHandoffModelAttributes(model, request);
            return "admin/booking-requests/detail";
        }

        try {
            var appointment = bookingRequestService.approve(id, approval);
            flash.addFlashAttribute("successMsg",
                    "✅ Solicitud aprobada. Cita #" + appointment.getId() +
                            " creada y confirmacion enviada.");
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("errorMsg", e.getMessage());
        }

        return "redirect:/admin/booking-requests";
    }

    // ── Rechazar ─────────────────────────────────────────────────────────────
    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id,
                         @RequestParam(required = false) String reason,
                         RedirectAttributes flash) {
        try {
            bookingRequestService.reject(id, reason);
            flash.addFlashAttribute("successMsg", "Solicitud rechazada y notificacion enviada.");
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/booking-requests";
    }

    private void addHandoffModelAttributes(Model model, cl.bunnycure.domain.model.BookingRequest request) {
        boolean handoffEnabled = appSettingsService.isWhatsappHandoffEnabled();
        model.addAttribute("whatsappHandoffEnabled", handoffEnabled);
        model.addAttribute("whatsappHandoffClientMessage", whatsAppHandoffService.buildClientHandoffMessage());
        model.addAttribute("whatsappHumanChannelLink", whatsAppHandoffService.buildHumanChannelLink());
        model.addAttribute("adminToCustomerHandoffLink", whatsAppHandoffService.buildAdminToCustomerLinkFromBookingRequest(request));
        model.addAttribute("whatsappHumanDisplayName", appSettingsService.getHumanWhatsappDisplayName());
    }
}
