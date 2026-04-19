package cl.bunnycure.web.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@RestController
public class WalletHeroImageController {
    private static final String DEFAULT_REWARD = "Premio BunnyCure";
    private final String rabbitIconDataUri;

    public WalletHeroImageController() {
        this.rabbitIconDataUri = loadRabbitIconDataUri();
    }

    @GetMapping(value = "/assets/wallet/hero_dynamic.svg", produces = "image/svg+xml")
    public ResponseEntity<String> dynamicHero(
            @RequestParam(name = "stamps", defaultValue = "0") Integer stamps,
            @RequestParam(name = "reward", defaultValue = "Premio BunnyCure") String reward
    ) {
        int boundedStamps = Math.max(0, Math.min(10, stamps == null ? 0 : stamps));
        String rewardText = normalizeReward(reward);
        String progressText = boundedStamps >= 10
                ? String.format(Locale.ROOT, "%d/10 SELLOS \u2014 PREMIO DISPONIBLE: %s", boundedStamps, rewardText)
                : String.format(Locale.ROOT, "%d/10 SELLOS \u2014 PROXIMO: %s", boundedStamps, rewardText);

        String svg = buildSvg(boundedStamps, progressText);
        return ResponseEntity.ok()
                .contentType(new MediaType("image", "svg+xml", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(30)).cachePublic())
                .body(svg);
    }

    private String buildSvg(int stamps, String progressText) {
        int[] cols = {0, 160, 320, 480, 640};
        int[] rows = {0, 90};
        List<int[]> positions = new ArrayList<>();
        for (int row : rows) {
            for (int col : cols) {
                positions.add(new int[]{col, row});
            }
        }

        StringBuilder markers = new StringBuilder();
        for (int i = 0; i < positions.size(); i++) {
            int[] p = positions.get(i);
            boolean isFilled = i < stamps;
            markers.append(String.format(
                    Locale.ROOT,
                    "<g transform=\"translate(%d,%d)\">"
                            + "<circle cx=\"0\" cy=\"0\" r=\"35\" fill=\"%s\" stroke=\"%s\" stroke-width=\"%s\" />"
                            + "<image href=\"%s\" x=\"-18\" y=\"-18\" width=\"36\" height=\"36\" opacity=\"%s\" preserveAspectRatio=\"xMidYMid meet\"/>"
                            + "</g>",
                    p[0], p[1],
                    isFilled ? "#c9897a" : "#f0f0f0",
                    isFilled ? "#7c3a2d" : "#d1d1d1",
                    isFilled ? "2" : "1.5",
                    rabbitIconDataUri,
                    isFilled ? "1" : "0.28"
            ));
        }

        return "<svg width=\"1032\" height=\"336\" viewBox=\"0 0 1032 336\" xmlns=\"http://www.w3.org/2000/svg\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#fdf6f3\" />"
                + "<text x=\"50%\" y=\"55\" font-family=\"Segoe UI, Roboto, sans-serif\" font-size=\"28\" font-weight=\"bold\" fill=\"#5c3d2e\" text-anchor=\"middle\" letter-spacing=\"1\">BUNNYCURE</text>"
                + "<text x=\"50%\" y=\"85\" font-family=\"Segoe UI, Roboto, sans-serif\" font-size=\"14\" fill=\"#9e7b6e\" text-anchor=\"middle\">Colecciona 10 sellos y reclama tu beneficio</text>"
                + "<g transform=\"translate(196,140)\">"
                + markers
                + "</g>"
                + "<rect x=\"165\" y=\"275\" width=\"702\" height=\"40\" rx=\"20\" fill=\"#ffffff\" stroke=\"#f0e0d8\" />"
                + "<text x=\"50%\" y=\"301\" font-family=\"Segoe UI, Roboto, sans-serif\" font-size=\"15\" font-weight=\"bold\" fill=\"#7c3a2d\" text-anchor=\"middle\">"
                + escapeXml(progressText)
                + "</text>"
                + "</svg>";
    }

    private String normalizeReward(String reward) {
        String trimmed = reward == null ? "" : reward.trim();
        if (trimmed.isBlank()) return DEFAULT_REWARD;
        if (trimmed.length() <= 40) return trimmed.toUpperCase(Locale.ROOT);
        return trimmed.substring(0, 37).toUpperCase(Locale.ROOT) + "...";
    }

    private String loadRabbitIconDataUri() {
        try {
            ClassPathResource resource = new ClassPathResource("static/assets/wallet/rabbit.png");
            byte[] bytes = resource.getInputStream().readAllBytes();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar assets/wallet/rabbit.png para Wallet hero", e);
        }
    }

    private String escapeXml(String raw) {
        return raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
