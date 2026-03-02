package cl.bunnycure.web.controller;

import cl.bunnycure.service.ServiceCatalogService;
import cl.bunnycure.web.dto.ServiceCatalogDto;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/services")
public class ServiceCatalogController extends BaseController {

    private final ServiceCatalogService service;

    public ServiceCatalogController(ServiceCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("services", service.findAll());
        return "admin/services/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("service", new ServiceCatalogDto());
        model.addAttribute("isNew", true);
        return "admin/services/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("service") ServiceCatalogDto dto,
                         BindingResult result, Model model,
                         RedirectAttributes flash) {
        if (result.hasErrors()) {
            model.addAttribute("isNew", true);
            return "admin/services/form";
        }
        service.save(dto);
        flash.addFlashAttribute("successMsg", "Servicio creado correctamente.");
        return "redirect:/admin/services";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var s = service.findById(id);
        var dto = new ServiceCatalogDto();
        dto.setId(s.getId());
        dto.setName(s.getName());
        dto.setDurationMinutes(s.getDurationMinutes());
        dto.setPrice(s.getPrice());
        dto.setDescription(s.getDescription());
        dto.setActive(s.getActive());
        dto.setDisplayOrder(s.getDisplayOrder());
        model.addAttribute("service", dto);
        model.addAttribute("isNew", false);
        return "admin/services/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("service") ServiceCatalogDto dto,
                         BindingResult result, Model model,
                         RedirectAttributes flash) {
        if (result.hasErrors()) {
            model.addAttribute("isNew", false);
            return "admin/services/form";
        }
        dto.setId(id);
        service.save(dto);
        flash.addFlashAttribute("successMsg", "Servicio actualizado correctamente.");
        return "redirect:/admin/services";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes flash) {
        service.toggleActive(id);
        flash.addFlashAttribute("successMsg", "Estado del servicio actualizado.");
        return "redirect:/admin/services";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        service.delete(id);
        flash.addFlashAttribute("successMsg", "Servicio eliminado.");
        return "redirect:/admin/services";
    }
}
