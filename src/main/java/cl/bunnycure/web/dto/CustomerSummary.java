package cl.bunnycure.web.dto;

import cl.bunnycure.domain.model.Customer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class CustomerSummary {

    @JsonIgnore
    private final Customer customer;
    private final long appointmentCount;

    // Delegados para Thymeleaf y API REST
    private final Long id;
    private final String publicId;
    private final String fullName;
    private final String phone;
    private final String rut;
    private final String email;
    private final String gender;
    private final LocalDate birthDate;
    private final String emergencyPhone;
    private final String healthNotes;
    private final String notes;
    private final Integer loyaltyStamps;
    private final Integer totalCompletedVisits;
    private final LocalDateTime createdAt;

    public CustomerSummary(Customer customer, long appointmentCount) {
        this.customer             = customer;
        this.appointmentCount     = appointmentCount;
        this.id                   = customer != null ? customer.getId() : null;
        this.publicId             = customer != null ? customer.getPublicId() : null;
        this.fullName             = customer != null ? customer.getFullName() : null;
        this.phone                = customer != null ? customer.getPhone() : null;
        this.rut                  = customer != null ? customer.getRut() : null;
        this.email                = customer != null ? customer.getEmail() : null;
        this.gender               = customer != null ? customer.getGender() : null;
        this.birthDate            = customer != null ? customer.getBirthDate() : null;
        this.emergencyPhone       = customer != null ? customer.getEmergencyPhone() : null;
        this.healthNotes          = customer != null ? customer.getHealthNotes() : null;
        this.notes                = customer != null ? customer.getNotes() : null;
        this.loyaltyStamps        = customer != null ? customer.getLoyaltyStamps() : null;
        this.totalCompletedVisits = customer != null ? customer.getTotalCompletedVisits() : null;
        this.createdAt            = customer != null ? customer.getCreatedAt() : null;
    }
}
