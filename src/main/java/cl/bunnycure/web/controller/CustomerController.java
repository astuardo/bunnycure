package cl.bunnycure.web.controller;

import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.service.CustomerService;
import cl.bunnycure.service.CustomerServiceRecordService;
import cl.bunnycure.web.dto.CustomerDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class CustomerController extends BaseController {

    private final CustomerService customerService;
    private final CustomerServiceRecordService customerServiceRecordService;

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
    @GetMapping("/{publicId}/edit")
    public String editForm(@PathVariable String publicId, Model model) {
        var customer = resolveCustomerByPublicIdOrLegacyId(publicId);
        if (!publicId.equals(customer.getPublicId())) {
            return "redirect:/customers/" + customer.getPublicId() + "/edit";
        }
        var dto = new CustomerDto();
        dto.setId(customer.getId());
        dto.setPublicId(customer.getPublicId());
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
    @PostMapping("/{publicId}/edit")
    public String update(@PathVariable String publicId,
                         @Valid @ModelAttribute("customer") CustomerDto dto,
                         BindingResult result,
                         Model model,
                         RedirectAttributes flash) {
        if (result.hasErrors()) {
            dto.setPublicId(resolveCustomerByPublicIdOrLegacyId(publicId).getPublicId());
            model.addAttribute("isNew", false);
            return "customers/form";
        }
        var customer = resolveCustomerByPublicIdOrLegacyId(publicId);
        customerService.updateByPublicId(customer.getPublicId(), dto);
        flash.addFlashAttribute("successMsg", "Cliente actualizado exitosamente.");
        return "redirect:/customers";
    }

    // ── Ver detalle / historial ───────────────────────────────────────────────
    @GetMapping("/{publicId}")
    public String detail(@PathVariable String publicId, Model model) {
        var customer = resolveCustomerByPublicIdOrLegacyIdWithAppointments(publicId);
        if (!publicId.equals(customer.getPublicId())) {
            return "redirect:/customers/" + customer.getPublicId();
        }
        model.addAttribute("customer", customer);
        model.addAttribute("serviceRecords", customerServiceRecordService.findLatestByCustomerId(customer.getId()));
        return "customers/detail";
    }

    @GetMapping("/{customerPublicId}/service-records/{recordId}/photo")
    @ResponseBody
    public ResponseEntity<byte[]> serviceRecordPhoto(@PathVariable String customerPublicId,
                                                     @PathVariable Long recordId) {
        return customerServiceRecordService.findById(recordId)
                .filter(record -> record.getCustomer() != null)
                .filter(record -> customerPublicId.equals(record.getCustomer().getPublicId()))
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
    @PostMapping("/{customerPublicId}/service-records/{recordId}/delete")
    public String deleteServiceRecord(@PathVariable String customerPublicId,
                                      @PathVariable Long recordId,
                                      RedirectAttributes flash) {
        var customer = resolveCustomerByPublicIdOrLegacyId(customerPublicId);
        boolean deleted = customerServiceRecordService.deleteByIdForCustomer(recordId, customer.getId());
        if (deleted) {
            flash.addFlashAttribute("successMsg", "Registro de servicio eliminado correctamente.");
        } else {
            flash.addFlashAttribute("errorMsg", "No se encontró el registro o no pertenece a este cliente.");
        }
        return "redirect:/customers/" + customer.getPublicId();
    }

    // ── Eliminar cliente ──────────────────────────────────────────────────────
    @PostMapping("/{publicId}/delete")
    public String delete(@PathVariable String publicId, RedirectAttributes flash) {
        var customer = resolveCustomerByPublicIdOrLegacyId(publicId);
        customerService.deleteByPublicId(customer.getPublicId());
        flash.addFlashAttribute("successMsg", "Cliente eliminado correctamente.");
        return "redirect:/customers";
    }

    private Customer resolveCustomerByPublicIdOrLegacyId(String value) {
        if (value != null && value.matches("\\d+")) {
            return customerService.findById(Long.parseLong(value));
        }
        return customerService.findByPublicId(value);
    }

    private Customer resolveCustomerByPublicIdOrLegacyIdWithAppointments(String value) {
        if (value != null && value.matches("\\d+")) {
            return customerService.findByIdWithAppointments(Long.parseLong(value));
        }
        return customerService.findByPublicIdWithAppointments(value);
    }


}