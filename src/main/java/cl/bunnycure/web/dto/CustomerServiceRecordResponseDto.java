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
public class CustomerServiceRecordResponseDto {
    private Long id;
    private Long customerId;
    private String serviceDetail;
    private String photoCaption;
    private String mimeType;
    private boolean hasPhoto;
    private LocalDateTime createdAt;
}
