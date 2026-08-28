package cl.bunnycure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchMarkManualRequestDto {
    private List<Long> appointmentIds;
    private String initialFolio;
    private String notes;
}
