package cl.bunnycure.service;

import cl.bunnycure.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductPageScrapingService {

    @Qualifier("simpleApiRestTemplate")
    private final RestTemplate restTemplate;

    public ProductScrapeResult scrape(String rawUrl) {
        String normalizedUrl = normalizeUrl(rawUrl);
        String html = fetchHtml(normalizedUrl);

        BigDecimal observedPrice = extractPrice(html).orElse(null);
        Boolean observedAvailable = detectAvailability(html);
        String productName = extractProductName(html).orElse(null);

        return new ProductScrapeResult(normalizedUrl, productName, observedPrice, observedAvailable);
    }

    private String normalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ValidationException("La URL de compra es obligatoria");
        }

        String candidate = rawUrl.trim();
        if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) {
            candidate = "https://" + candidate;
        }

        try {
            URI uri = new URI(candidate);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new ValidationException("La URL no es válida");
            }
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new ValidationException("La URL debe usar http o https");
            }
            return uri.toString();
        } catch (URISyntaxException e) {
            throw new ValidationException("La URL no es válida");
        }
    }

    private String fetchHtml(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.TEXT_HTML, MediaType.APPLICATION_XHTML_XML, MediaType.ALL));
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; BunnyCureBot/1.0; +https://bunnycure.cl)");
            headers.setAcceptCharset(List.of(StandardCharsets.UTF_8));

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String body = restTemplate.exchange(URI.create(url), HttpMethod.GET, entity, String.class).getBody();
            if (body == null || body.isBlank()) {
                throw new ValidationException("No se pudo leer contenido desde la URL indicada");
            }
            return body;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Inventory-Import] Could not fetch url {}: {}", url, e.getMessage());
            throw new ValidationException("No se pudo acceder al link del producto");
        }
    }

    private Optional<String> extractProductName(String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }

        List<Pattern> patterns = List.of(
                Pattern.compile("(?is)<meta\\s+property=[\"']og:title[\"'][^>]*content=[\"']([^\"']+)[\"'][^>]*>"),
                Pattern.compile("(?is)<meta\\s+name=[\"']twitter:title[\"'][^>]*content=[\"']([^\"']+)[\"'][^>]*>"),
                Pattern.compile("(?is)<title>(.*?)</title>"),
                Pattern.compile("(?is)\"name\"\\s*:\\s*\"([^\"]{2,200})\"")
        );

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                String value = cleanText(matcher.group(1));
                if (value != null && !value.isBlank()) {
                    return Optional.of(value);
                }
            }
        }

        return Optional.empty();
    }

    private Optional<BigDecimal> extractPrice(String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }

        List<Pattern> patterns = List.of(
                Pattern.compile("(?is)f-price-item--sale[^>]*>\\s*(?:<s>)?\\s*\\$?\\s*([0-9][0-9.,\\s]*)\\s*<"),
                Pattern.compile("(?is)f-price-item--regular[^>]*>\\s*\\$?\\s*([0-9][0-9.,\\s]*)\\s*<"),
                Pattern.compile("(?i)Precio de venta\\s*\\$\\s*([0-9.,]+)"),
                Pattern.compile("(?i)Precio regular\\s*\\$\\s*([0-9.,]+)"),
                Pattern.compile("(?i)product:price:amount\"\\s*content=\"([0-9.,]+)\""),
                Pattern.compile("(?i)\"price\"\\s*:\\s*\"?([0-9.,]+)\"?"),
                Pattern.compile("(?i)\\$\\s*([0-9]{1,3}(?:\\.[0-9]{3})*(?:,[0-9]{1,2})?)")
        );

        List<String> candidates = new ArrayList<>();
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                String candidate = matcher.group(1);
                if (candidate != null && !candidate.isBlank()) {
                    candidates.add(candidate);
                }
            }
        }

        for (String candidate : candidates) {
            BigDecimal parsed = parseMoney(candidate);
            if (parsed != null) {
                return Optional.of(parsed);
            }
        }

        return Optional.empty();
    }

    private BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim().replaceAll("\\s", "").replace("$", "");
        int lastDot = normalized.lastIndexOf('.');
        int lastComma = normalized.lastIndexOf(',');

        try {
            if (lastComma >= 0) {
                normalized = normalized.replace(".", "").replace(",", ".");
            } else if (lastDot >= 0) {
                boolean looksLikeThousandSeparated = normalized.matches("^\\d{1,3}(?:\\.\\d{3})+$");
                if (looksLikeThousandSeparated) {
                    normalized = normalized.replace(".", "");
                }
            }

            return new BigDecimal(normalized);
        } catch (Exception e) {
            log.debug("[Inventory-Import] Could not parse money value '{}': {}", raw, e.getMessage());
            return null;
        }
    }

    private Boolean detectAvailability(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }

        Pattern submitButtonPattern = Pattern.compile(
                "(?is)<button[^>]*id\\s*=\\s*[\"']ProductSubmitButton[^\"']*[\"'][^>]*>(.*?)</button>"
        );
        Matcher submitMatcher = submitButtonPattern.matcher(html);
        if (submitMatcher.find()) {
            String buttonHtml = submitMatcher.group(0).toLowerCase(Locale.ROOT);
            String buttonText = submitMatcher.group(1).replaceAll("(?is)<[^>]+>", " ").toLowerCase(Locale.ROOT);
            if (buttonHtml.contains("disabled") || buttonText.contains("agotado")) {
                return Boolean.FALSE;
            }
            if (buttonText.contains("agregar al carrito") || buttonText.contains("add to cart")) {
                return Boolean.TRUE;
            }
        }

        String lower = html.toLowerCase(Locale.ROOT);
        if (lower.contains("agotado") || lower.contains("sold out") || lower.contains("sin stock") || lower.contains("no disponible")) {
            return Boolean.FALSE;
        }

        return Boolean.TRUE;
    }

    private String cleanText(String raw) {
        if (raw == null) return null;
        return raw
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record ProductScrapeResult(
            String normalizedUrl,
            String productName,
            BigDecimal observedPrice,
            Boolean observedAvailable
    ) {}
}

