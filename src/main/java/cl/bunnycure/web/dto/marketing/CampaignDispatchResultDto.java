package cl.bunnycure.web.dto.marketing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignDispatchResultDto {
    private String templateName;
    private AudienceType audienceType;
    private int totalTargeted;
    private int sentCount;
    private int failedCount;
    private List<String> errorMessages;
    private boolean isTestRun;
}
