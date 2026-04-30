package cl.bunnycure.web.dto;

import cl.bunnycure.domain.enums.GiftCardPaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class GiftCardCreateRequestDto {

    @NotBlank(message = "beneficiaryFullName es obligatorio")
    @Size(min = 2, max = 120)
    private String beneficiaryFullName;

    @NotBlank(message = "beneficiaryPhone es obligatorio")
    @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Formato de teléfono inválido")
    private String beneficiaryPhone;

    @Email(message = "Email inválido")
    private String beneficiaryEmail;

    @Size(max = 120)
    private String buyerName;

    @Pattern(regexp = "^$|^\\+?[0-9]{8,15}$", message = "Formato de teléfono de compradora inválido")
    private String buyerPhone;

    @Email(message = "Email de compradora inválido")
    private String buyerEmail;

    @NotNull(message = "expiresOn es obligatorio")
    private LocalDate expiresOn;

    @NotNull(message = "paidAmount es obligatorio")
    @Min(value = 0, message = "paidAmount no puede ser negativo")
    private Integer paidAmount;

    @NotNull(message = "paymentMethod es obligatorio")
    private GiftCardPaymentMethod paymentMethod;

    @NotEmpty(message = "Debe incluir al menos un servicio")
    @Valid
    private List<GiftCardItemRequestDto> items;
}
