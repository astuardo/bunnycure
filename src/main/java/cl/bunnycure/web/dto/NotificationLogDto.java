package cl.bunnycure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLogDto {
    private Long id;
    private Long appointmentId;
    private Long customerId;
    private String customerName;
    private String channel;
    private String recipient;
    private String subject;
    private String content;
    private String wamid;
    private String status;
    private LocalDateTime createdAt;
}
