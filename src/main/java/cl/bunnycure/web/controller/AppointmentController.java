package cl.bunnycure.web.controller;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.service.AppointmentService;
import cl.bunnycure.service.AppSettingsService;
import cl.bunnycure.service.CustomerService;
import cl.bunnycure.service.ServiceCatalogService;
import cl.bunnycure.service.WhatsAppHandoffService;
import cl.bunnycure.web.dto.AppointmentDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentController extends BaseController {

    public record CalendarDayCell(LocalDate date, boolean today, boolean selected, boolean outsideCurrentMonth,
                                  long appointmentCount) {
            public int getDotCount() {
                return Math.max(0, (int) appointmentCount);
            }

            public String getMiniLabel() {
                if (appointmentCount <= 0) {
                    return "";
                }
                return appointmentCount == 1 ? "1 cita" : appointmentCount + " citas";
            }
        }

    private final AppointmentService appointmentService;
    private final CustomerService customerService;
    private final ServiceCatalogService serviceCatalogService; // ✅ campo declarado
    private final WhatsAppHandoffService whatsAppHandoffService;
    private final AppSettingsService appSettingsService;

    @GetMapping
    public String list(@RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       @RequestParam(required = false, defaultValue = "week") String view,
                       Model model) {
        LocalDate today = LocalDate.now();
        LocalDate base = (date != null) ? date : today;
        String viewMode = normalizeViewMode(view);
        Locale appLocale = appSettingsService.getAppJavaLocale();

        LocalDate rangeStart;
        LocalDate rangeEnd;
        LocalDate prevDate;
        LocalDate nextDate;
        String rangeLabel;

        if ("day".equals(viewMode)) {
            rangeStart = base;
            rangeEnd = base;
            prevDate = base.minusDays(1);
            nextDate = base.plusDays(1);
            rangeLabel = base.format(DateTimeFormatter.ofPattern("EEEE dd 'de' MMMM yyyy", appLocale));
        } else if ("month".equals(viewMode)) {
            rangeStart = base.withDayOfMonth(1);
            rangeEnd = rangeStart.with(TemporalAdjusters.lastDayOfMonth());
            prevDate = rangeStart.minusMonths(1);
            nextDate = rangeStart.plusMonths(1);
            rangeLabel = rangeStart.format(DateTimeFormatter.ofPattern("MMMM yyyy", appLocale));
        } else {
            rangeStart = base.with(java.time.DayOfWeek.MONDAY);
            rangeEnd = rangeStart.plusDays(6);
            prevDate = rangeStart.minusWeeks(1);
            nextDate = rangeStart.plusWeeks(1);
            rangeLabel = String.format("%s - %s",
                    rangeStart.format(DateTimeFormatter.ofPattern("dd MMM", appLocale)),
                    rangeEnd.format(DateTimeFormatter.ofPattern("dd MMM yyyy", appLocale)));
        }

        List<Appointment> appointments = appointmentService.findByDateRange(rangeStart, rangeEnd);
        Map<Long, String> appointmentHandoffLinks = new LinkedHashMap<>();
        for (Appointment appointment : appointments) {
            if (appointment.getId() != null) {
                appointmentHandoffLinks.put(
                        appointment.getId(),
                        whatsAppHandoffService.buildAdminToCustomerLinkFromAppointment(appointment)
                );
            }
        }

        model.addAttribute("appointments", appointments);
        model.addAttribute("appointmentHandoffLinks", appointmentHandoffLinks);
        model.addAttribute("whatsappHandoffEnabled", appSettingsService.isWhatsappHandoffEnabled());
        model.addAttribute("whatsappHumanDisplayName", appSettingsService.getHumanWhatsappDisplayName());
        model.addAttribute("whatsappHumanChannelLink", whatsAppHandoffService.buildHumanChannelLink());
        model.addAttribute("viewMode", viewMode);
        model.addAttribute("selectedDate", base);
        model.addAttribute("rangeStart", rangeStart);
        model.addAttribute("rangeEnd", rangeEnd);
        model.addAttribute("rangeLabel", rangeLabel);
        model.addAttribute("prevDate", prevDate);
        model.addAttribute("nextDate", nextDate);
        model.addAttribute("today", today);
        model.addAttribute("todayDate", today);

        if ("month".equals(viewMode)) {
            LocalDate calendarStart = rangeStart.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            LocalDate calendarEnd = rangeEnd.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));

            List<LocalDate> calendarDays = calendarStart.datesUntil(calendarEnd.plusDays(1)).toList();
            Map<LocalDate, Long> appointmentCountByDate = new LinkedHashMap<>();
            for (LocalDate day : calendarDays) {
                appointmentCountByDate.put(day, 0L);
            }
            for (Appointment appointment : appointments) {
                LocalDate appointmentDate = appointment.getAppointmentDate();
                appointmentCountByDate.computeIfPresent(appointmentDate, (d, count) -> count + 1);
            }

            List<CalendarDayCell> calendarDayCells = new ArrayList<>();
            for (LocalDate day : calendarDays) {
                long count = appointmentCountByDate.getOrDefault(day, 0L);
                calendarDayCells.add(new CalendarDayCell(
                        day,
                        day.equals(today),
                        day.equals(base),
                        day.getMonthValue() != rangeStart.getMonthValue(),
                        count
                ));
            }

            List<Appointment> selectedDayAppointments = appointments.stream()
                    .filter(a -> a.getAppointmentDate() != null && a.getAppointmentDate().equals(base))
                    .toList();

            model.addAttribute("calendarDays", calendarDays);
            model.addAttribute("appointmentCountByDate", appointmentCountByDate);
            model.addAttribute("calendarDayCells", calendarDayCells);
            model.addAttribute("selectedDayAppointments", selectedDayAppointments);
            model.addAttribute("weekDayNames", buildWeekDayNames(appLocale));
            model.addAttribute("monthStart", rangeStart);
        }

        return "appointments/list";
    }

    private String normalizeViewMode(String view) {
        if (view == null) {
            return "week";
        }
        return switch (view.trim().toLowerCase(Locale.ROOT)) {
            case "day" -> "day";
            case "month" -> "month";
            default -> "week";
        };
    }

    private List<String> buildWeekDayNames(Locale locale) {
        return List.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY
        ).stream().map(day -> {
            String shortName = day.getDisplayName(TextStyle.SHORT, locale);
            return shortName.replace(".", "");
        }).toList();
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
    public String newForm(@RequestParam(required = false) Long customerId,
                          @RequestParam(required = false) String customerPublicId,
                          Model model) {
        var dto = AppointmentDto.builder()
                .appointmentDate(LocalDate.now().plusDays(1))
                .build();
        if (customerId != null) {
            dto.setCustomerId(customerId);
        } else if (customerPublicId != null && !customerPublicId.isBlank()) {
            dto.setCustomerId(customerService.findByPublicId(customerPublicId).getId());
        }

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
        try {
            appointmentService.deleteAppointment(id);
            flash.addFlashAttribute("successMsg", "Cita eliminada correctamente.");
        } catch (DataIntegrityViolationException ex) {
            flash.addFlashAttribute("errorMsg", "No se puede eliminar la cita porque esta vinculada a una solicitud.");
        }
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