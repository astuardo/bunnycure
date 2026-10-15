package cl.bunnycure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * DTO para la especificación estándar W3C Web App Manifest (PWA).
 * Permite la instalación nativa personalizada en iOS Safari (A2HS) y Android Chrome.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PwaManifestDto implements Serializable {

    private String id;
    private String name;

    @JsonProperty("short_name")
    private String shortName;

    private String description;

    @JsonProperty("theme_color")
    private String themeColor;

    @JsonProperty("background_color")
    private String backgroundColor;

    private String display;
    private String scope;

    @JsonProperty("start_url")
    private String startUrl;

    private String orientation;
    private List<ManifestIcon> icons;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManifestIcon implements Serializable {
        private String src;
        private String sizes;
        private String type;
        private String purpose;
    }
}
