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
public class AudiencePreviewDto {
    private AudienceType audienceType;
    private String audienceDescription;
    private int totalCount;
    private List<SampleRecipientDto> sampleRecipients;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SampleRecipientDto {
        private Long id;
        private String fullName;
        private String maskedPhone;
        private String lastVisitDate;
        private int completedVisits;
    }
}
