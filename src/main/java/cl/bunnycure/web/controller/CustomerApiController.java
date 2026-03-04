package cl.bunnycure.web.controller;

import cl.bunnycure.service.CustomerService;
import cl.bunnycure.web.dto.CustomerLookupResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerApiController {

    private final CustomerService customerService;

    public CustomerApiController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /**
     * Busca una clienta existente por número de teléfono.
     * Retorna los datos de la clienta si existe, o un objeto vacío si no.
     *
     * @param phone Número de teléfono en formato +56XXXXXXXXX
     * @return CustomerLookupResponseDto con datos de la clienta o null si no existe
     */
    @PostMapping("/lookup")
    public ResponseEntity<CustomerLookupResponseDto> lookup(@RequestParam String phone) {
        CustomerLookupResponseDto response = customerService.findByPhoneForLookup(phone);
        return ResponseEntity.ok(response);
    }
}
