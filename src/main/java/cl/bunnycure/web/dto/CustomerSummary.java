package cl.bunnycure.web.dto;

import cl.bunnycure.domain.model.Customer;
import lombok.Getter;

@Getter
public class CustomerSummary {

    private final Customer customer;
    private final long appointmentCount;

    // Delegados para Thymeleaf
    private final Long id;
    private final String publicId;
    private final String fullName;
    private final String phone;
    private final String email;

    public CustomerSummary(Customer customer, long appointmentCount) {
        this.customer         = customer;
        this.appointmentCount = appointmentCount;
        this.id               = customer.getId();
        this.publicId         = customer.getPublicId();
        this.fullName         = customer.getFullName();
        this.phone            = customer.getPhone();
        this.email            = customer.getEmail();
    }
}