package cl.bunnycure.web.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WebPushSubscriptionResponseDto {
    private Long id;
    private String endpoint;
    private boolean active;
}
