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
public class MarketingTemplateDto {
    private String name;
    private String displayName;
    private String occasion;
    private String emoji;
    private String category;
    private String language;
    private String metaStatus; // APPROVED, PENDING, REJECTED, NOT_REGISTERED
    private String metaId;
    private String headerText;
    private String bodyText;
    private String footerText;
    private String buttonText;
    private String buttonUrl;
    private List<String> sampleVariables;
}
