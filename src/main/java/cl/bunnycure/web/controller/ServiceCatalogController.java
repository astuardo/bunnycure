package cl.bunnycure.web.controller;

import cl.bunnycure.service.ServiceCatalogService;
import cl.bunnycure.web.dto.ServiceCatalogDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin/services")
@RequiredArgsConstructor
public class ServiceCatalogController extends BaseController {

    private final ServiceCatalogService service;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("services", service.findAll());
        return "admin/services/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("service", new ServiceCatalogDto());
        model.addAttribute("isNew", true);
        populateAvailableServices(model, null);
        return "admin/services/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("service") ServiceCatalogDto dto,
                         BindingResult result, Model model,
                         RedirectAttributes flash) {
        validateServiceBusinessRules(dto, result);
        
        if (result.hasErrors()) {
            model.addAttribute("isNew", true);
            populateAvailableServices(model, null);
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
        dto.setCompatibleServiceIds(s.getCompatibleServices().stream().map(cl.bunnycure.domain.model.ServiceCatalog::getId).toList());
        model.addAttribute("service", dto);
        model.addAttribute("isNew", false);
        populateAvailableServices(model, id);
        return "admin/services/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("service") ServiceCatalogDto dto,
                         BindingResult result, Model model,
                         RedirectAttributes flash) {
        validateServiceBusinessRules(dto, result);
        
        if (result.hasErrors()) {
            model.addAttribute("isNew", false);
            populateAvailableServices(model, id);
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
        try {
            ServiceCatalogService.DeleteOutcome outcome = service.delete(id);
            if (outcome == ServiceCatalogService.DeleteOutcome.DELETED) {
                flash.addFlashAttribute("successMsg", "Servicio eliminado.");
            } else {
                flash.addFlashAttribute("errorMsg", "El servicio tiene citas o solicitudes asociadas. Se ocultó del catálogo en lugar de eliminarse.");
            }
        } catch (DataIntegrityViolationException ex) {
            flash.addFlashAttribute("errorMsg", "No se puede eliminar el servicio porque está en uso. Se recomienda ocultarlo.");
        }
        return "redirect:/admin/services";
    }

    private void validateServiceBusinessRules(ServiceCatalogDto dto, BindingResult result) {
        // Validar que el nombre no esté duplicado (ignorando mayúsculas/minúsculas)
        if (dto.getName() != null) {
            String trimmedName = dto.getName().trim();
            var existingServices = service.findAll();
            boolean isDuplicate = existingServices.stream()
                .anyMatch(s -> !s.getId().equals(dto.getId()) && 
                              s.getName().trim().equalsIgnoreCase(trimmedName));
            if (isDuplicate) {
                result.rejectValue("name", "duplicate", 
                    "Ya existe un servicio con este nombre");
            }
        }

        // Validar que la duración sea múltiplo de 15 (para facilitar agendamiento)
        if (dto.getDurationMinutes() != null && dto.getDurationMinutes() % 15 != 0) {
            result.rejectValue("durationMinutes", "notMultiple", 
                "La duración debe ser múltiplo de 15 minutos para facilitar el agendamiento");
        }

        // Validar coherencia precio-duración (advertencia si el precio por minuto es muy bajo)
        if (dto.getPrice() != null && dto.getDurationMinutes() != null && 
            dto.getDurationMinutes() > 0) {
            double pricePerMinute = dto.getPrice().doubleValue() / dto.getDurationMinutes();
            if (pricePerMinute < 50) { // Menos de $50 por minuto
                result.rejectValue("price", "tooLow", 
                    "El precio parece muy bajo para la duración especificada (menos de $50 por minuto). Verifique el valor.");
            }
        }
    }

    private void populateAvailableServices(Model model, Long currentServiceId) {
        List<cl.bunnycure.domain.model.ServiceCatalog> availableServices = new ArrayList<>(service.findAll());
        if (currentServiceId != null) {
            availableServices.removeIf(s -> s.getId().equals(currentServiceId));
        }
        model.addAttribute("availableServices", availableServices);
    }
}
