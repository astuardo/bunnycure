package cl.bunnycure.web.controller;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.service.AppointmentService;
import cl.bunnycure.service.CustomerService;
import cl.bunnycure.service.ServiceCatalogService;
import cl.bunnycure.web.dto.AppointmentDto;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/appointments")
public class AppointmentController extends BaseController {

    private final AppointmentService appointmentService;
    private final CustomerService customerService;
    private final ServiceCatalogService serviceCatalogService; // ✅ campo declarado

    public AppointmentController(AppointmentService appointmentService,
                                 CustomerService customerService,
                                 ServiceCatalogService serviceCatalogService) {
        this.appointmentService    = appointmentService;
        this.customerService       = customerService;
        this.serviceCatalogService = serviceCatalogService;
    }

    @GetMapping
    public String list(@RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       Model model) {
        LocalDate base      = (date != null) ? date : LocalDate.now();
        LocalDate weekStart = base.with(java.time.DayOfWeek.MONDAY);
        LocalDate weekEnd   = weekStart.plusDays(6);

        model.addAttribute("appointments", appointmentService.findByDateRange(weekStart, weekEnd));
        model.addAttribute("weekStart", weekStart);
        model.addAttribute("weekEnd",   weekEnd);
        model.addAttribute("prevWeek",  weekStart.minusWeeks(1));
        model.addAttribute("nextWeek",  weekStart.plusWeeks(1));
        model.addAttribute("today",     LocalDate.now());
        return "appointments/list";
    }

    @GetMapping("/new-from-wa")
    public String newFromWhatsApp(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String block,
            Model model) {
        var dto = AppointmentDto.builder()
                .appointmentDate(LocalDate.now().plusDays(1))
                .build();

        // Intentar parsear la fecha si viene
        if (date != null && !date.isBlank()) {
            try {
                dto.setAppointmentDate(LocalDate.parse(date));
            } catch (Exception e) {
                // Si falla, usar fecha por defecto
            }
        }

        model.addAttribute("appointment", dto);
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("serviceTypes", serviceCatalogService.findAllActive());
        model.addAttribute("isNew", true);
        model.addAttribute("waName", name);
        model.addAttribute("waPhone", phone);
        model.addAttribute("waService", service);
        model.addAttribute("waBlock", block);
        return "appointments/form";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long customerId, Model model) {
        var dto = AppointmentDto.builder()
                .appointmentDate(LocalDate.now().plusDays(1))
                .build();
        if (customerId != null) dto.setCustomerId(customerId);

        model.addAttribute("appointment",  dto);
        model.addAttribute("customers",    customerService.findAll());
        model.addAttribute("serviceTypes", serviceCatalogService.findAllActive()); // ✅
        model.addAttribute("isNew", true);
        return "appointments/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("appointment") AppointmentDto dto,
                         BindingResult result,
                         Model model,
                         RedirectAttributes flash) {
        if (result.hasErrors()) {
            model.addAttribute("customers",    customerService.findAll());
            model.addAttribute("serviceTypes", serviceCatalogService.findAllActive()); // ✅
            model.addAttribute("isNew", true);
            return "appointments/form";
        }
        appointmentService.createAppointment(dto);
        flash.addFlashAttribute("successMsg", "Cita agendada exitosamente.");
        return "redirect:/appointments";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        var apt = appointmentService.findById(id);
        var dto = toDto(apt);

        model.addAttribute("appointment",  dto);
        model.addAttribute("aptEntity",    apt);
        model.addAttribute("customers",    customerService.findAll());
        model.addAttribute("serviceTypes", serviceCatalogService.findAllActive()); // ✅
        model.addAttribute("statusValues", AppointmentStatus.values());
        model.addAttribute("isNew", false);
        model.addAttribute("dateValue",
                apt.getAppointmentDate().format(
                        java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
        model.addAttribute("timeValue",
                apt.getAppointmentTime().format(
                        java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        return "appointments/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("appointment") AppointmentDto dto,
                         BindingResult result,
                         Model model,
                         RedirectAttributes flash) {
        if (result.hasErrors()) {
            model.addAttribute("customers",    customerService.findAll());
            model.addAttribute("serviceTypes", serviceCatalogService.findAllActive()); // ✅
            model.addAttribute("statusValues", AppointmentStatus.values());
            model.addAttribute("isNew", false);
            return "appointments/form";
        }
        appointmentService.updateAppointment(id, dto);
        flash.addFlashAttribute("successMsg", "Cita actualizada correctamente.");
        return "redirect:/appointments";
    }

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable Long id,
                               @RequestParam AppointmentStatus status,
                               RedirectAttributes flash) {
        appointmentService.updateStatus(id, status);
        flash.addFlashAttribute("successMsg", "Estado actualizado.");
        return "redirect:/appointments";
    }

    @PostMapping("/{id}/notify")
    public String renotify(@PathVariable Long id, RedirectAttributes flash) {
        try {
            appointmentService.sendManualNotification(id);
            flash.addFlashAttribute("successMsg", "Notificación enviada correctamente.");
        } catch (Exception e) {
            flash.addFlashAttribute("errorMsg",
                    "Error al enviar la notificación: " + e.getMessage());
        }
        return "redirect:/appointments";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        appointmentService.deleteAppointment(id);
        flash.addFlashAttribute("successMsg", "Cita eliminada correctamente.");
        return "redirect:/appointments";
    }

    private AppointmentDto toDto(Appointment apt) {
        return AppointmentDto.builder()
                .id(apt.getId())
                .customerId(apt.getCustomer().getId())
                .serviceId(apt.getService().getId())
                .appointmentDate(apt.getAppointmentDate())
                .appointmentTime(apt.getAppointmentTime())
                .status(apt.getStatus())
                .observations(apt.getObservations())
                .build();
    }
}