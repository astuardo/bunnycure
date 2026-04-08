package cl.bunnycure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WebPushSubscriptionRequestDto {

    @NotBlank
    private String endpoint;

    @Valid
    @NotNull
    private Keys keys;

    @Getter
    @Setter
    public static class Keys {
        @NotBlank
        private String p256dh;

        @NotBlank
        private String auth;
    }
}
