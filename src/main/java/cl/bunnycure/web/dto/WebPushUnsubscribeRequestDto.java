package cl.bunnycure.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WebPushUnsubscribeRequestDto {
    @NotBlank
    private String endpoint;
}
