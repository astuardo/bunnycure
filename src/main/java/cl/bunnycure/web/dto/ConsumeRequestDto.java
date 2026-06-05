package cl.bunnycure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ConsumeRequestDto {

    @NotNull
    private Long serviceId;

    @NotEmpty
    @Valid
    private List<MaterialUsageDto> usages;

    private Long usedByUserId;
}
