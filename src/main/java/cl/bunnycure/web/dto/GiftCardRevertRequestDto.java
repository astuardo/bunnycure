package cl.bunnycure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GiftCardRevertRequestDto {

    @NotBlank(message = "note es obligatoria")
    private String note;

    @Valid
    @NotEmpty(message = "Debe incluir al menos un item para revertir")
    private List<GiftCardRedeemItemDto> items;
}
