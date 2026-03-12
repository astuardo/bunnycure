package cl.bunnycure.web.controller;

import cl.bunnycure.service.CustomerService;
import cl.bunnycure.service.CustomerServiceRecordService;
import cl.bunnycure.web.dto.CustomerDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customers")
public class CustomerController extends BaseController {

    private final CustomerService customerService;
    private final CustomerServiceRecordService customerServiceRecordService;

    public CustomerController(CustomerService customerService,
                              CustomerServiceRecordService customerServiceRecordService) {
        this.customerService = customerService;
        this.customerServiceRecordService = customerServiceRecordService;
    }

    // ── Lista ─────────────────────────────────────────────────────────────────
    @GetMapping
    public String list(@RequestParam(required = false) String search, Model model) {
        var customers = (search != null && !search.isBlank())
                ? customerService.searchSummary(search)
                : customerService.findAllSummary();     // ← usa el nuevo método

        model.addAttribute("customers", customers);
        model.addAttribute("search", search);
        return "customers/list";
    }


    // ── Formulario nuevo ──────────────────────────────────────────────────────
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("customer", new CustomerDto());
        model.addAttribute("isNew", true);
        return "customers/form";
    }

    // ── Guardar nuevo ─────────────────────────────────────────────────────────
    @PostMapping
    public String create(@Valid @ModelAttribute("customer") CustomerDto dto,
                         BindingResult result,
                         Model model,
                         RedirectAttributes flash) {
        if (result.hasErrors()) {
            model.addAttribute("isNew", true);
            return "customers/form";
        }
        try {
            customerService.create(dto);
            flash.addFlashAttribute("successMsg", "Cliente creado exitosamente.");
        } catch (IllegalArgumentException e) {
            model.addAttribute("isNew", true);
            model.addAttribute("errorMsg", e.getMessage());
            return "customers/form";
        }
        return "redirect:/customers";
    }

    // ── Formulario editar ─────────────────────────────────────────────────────
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var customer = customerService.findById(id);
        var dto = new CustomerDto();
        dto.setId(customer.getId());
        dto.setFullName(customer.getFullName());
        dto.setPhone(customer.getPhone());
        dto.setEmail(customer.getEmail());
        dto.setGender(customer.getGender());
        dto.setBirthDate(customer.getBirthDate());
        dto.setEmergencyPhone(customer.getEmergencyPhone());
        dto.setHealthNotes(customer.getHealthNotes());
        dto.setNotes(customer.getNotes());

        model.addAttribute("customer", dto);
        model.addAttribute("isNew", false);
        return "customers/form";
    }

    // ── Actualizar ────────────────────────────────────────────────────────────
    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("customer") CustomerDto dto,
                         BindingResult result,
                         Model model,
                         RedirectAttributes flash) {
        if (result.hasErrors()) {
            model.addAttribute("isNew", false);
            return "customers/form";
        }
        customerService.update(id, dto);
        flash.addFlashAttribute("successMsg", "Cliente actualizado exitosamente.");
        return "redirect:/customers";
    }

    // ── Ver detalle / historial ───────────────────────────────────────────────
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("customer", customerService.findByIdWithAppointments(id));
        model.addAttribute("serviceRecords", customerServiceRecordService.findLatestByCustomerId(id));
        return "customers/detail";
    }

    @GetMapping("/{customerId}/service-records/{recordId}/photo")
    @ResponseBody
    public ResponseEntity<byte[]> serviceRecordPhoto(@PathVariable Long customerId,
                                                     @PathVariable Long recordId) {
        return customerServiceRecordService.findById(recordId)
                .filter(record -> record.getCustomer() != null && customerId.equals(record.getCustomer().getId()))
                .filter(record -> record.getPhotoData() != null && record.getPhotoData().length > 0)
                .map(record -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(resolveMediaType(record.getMimeType()));
                    return new ResponseEntity<>(record.getPhotoData(), headers, HttpStatus.OK);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private MediaType resolveMediaType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    // ── Eliminar registro de servicio ─────────────────────────────────────────
    @PostMapping("/{customerId}/service-records/{recordId}/delete")
    public String deleteServiceRecord(@PathVariable Long customerId,
                                      @PathVariable Long recordId,
                                      RedirectAttributes flash) {
        boolean deleted = customerServiceRecordService.deleteByIdForCustomer(recordId, customerId);
        if (deleted) {
            flash.addFlashAttribute("successMsg", "Registro de servicio eliminado correctamente.");
        } else {
            flash.addFlashAttribute("errorMsg", "No se encontró el registro o no pertenece a este cliente.");
        }
        return "redirect:/customers/" + customerId;
    }

    // ── Eliminar cliente ──────────────────────────────────────────────────────
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        customerService.delete(id);
        flash.addFlashAttribute("successMsg", "Cliente eliminado correctamente.");
        return "redirect:/customers";
    }


}