package cl.bunnycure.web.controller;

import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.service.CustomerService;
import cl.bunnycure.service.GoogleWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Slf4j
@RestController
@RequiredArgsConstructor
public class WalletRedirectController {

    private final CustomerService customerService;
    private final GoogleWalletService googleWalletService;

    @GetMapping("/w/{publicId}")
    public ResponseEntity<Void> walletRedirect(@PathVariable String publicId) {
        Customer customer = customerService.findByPublicId(publicId);
        String walletUrl = googleWalletService.createWalletLink(customer);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(walletUrl).toString())
                .build();
    }
}
