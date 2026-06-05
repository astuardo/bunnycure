package cl.bunnycure.service;

import cl.bunnycure.domain.model.Product;
import cl.bunnycure.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductPriceMonitoringService {

    private final ProductRepository productRepository;

    @Qualifier("simpleApiRestTemplate")
    private final RestTemplate restTemplate;

    @Scheduled(cron = "${bunnycure.inventory.price-monitor.cron:0 30 8 * * *}", zone = "${bunnycure.scheduler.timezone:America/Santiago}")
    public void refreshAllProducts() {
        List<Product> products = productRepository.findAllWithPurchaseUrl();
        for (Product product : products) {
            try {
                refreshProduct(product);
            } catch (Exception e) {
                log.warn("[Inventory-Monitor] Could not refresh product {} ({}): {}", product.getId(), product.getName(), e.getMessage());
            }
        }
    }

    public Product refreshProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no existe: " + productId));
        return refreshProduct(product);
    }

    public Product refreshProduct(Product product) {
        if (product.getPurchaseUrl() == null || product.getPurchaseUrl().isBlank()) {
            throw new IllegalArgumentException("El producto no tiene URL de compra para monitorear");
        }

        String html = fetchHtml(product.getPurchaseUrl());
        BigDecimal observedPrice = extractPrice(html).orElse(product.getObservedPrice());
        Boolean observedAvailable = detectAvailability(html);

        product.setObservedPrice(observedPrice);
        product.setObservedAvailable(observedAvailable);
        product.setLastObservedAt(OffsetDateTime.now());

        Product saved = productRepository.save(product);
        log.info("[Inventory-Monitor] Refreshed product {} observedPrice={} observedAvailable={}",
                product.getId(), observedPrice, observedAvailable);
        return saved;
    }

    private String fetchHtml(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.TEXT_HTML, MediaType.APPLICATION_XHTML_XML, MediaType.ALL));
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; BunnyCureBot/1.0; +https://bunnycure.cl)");
        headers.setAcceptCharset(List.of(StandardCharsets.UTF_8));

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(URI.create(url), HttpMethod.GET, entity, String.class).getBody();
    }

    private java.util.Optional<BigDecimal> extractPrice(String html) {
        if (html == null || html.isBlank()) {
            return java.util.Optional.empty();
        }

        List<Pattern> patterns = List.of(
                Pattern.compile("(?i)Precio de venta\\s*\\$\\s*([0-9.,]+)"),
                Pattern.compile("(?i)Precio regular\\s*\\$\\s*([0-9.,]+)"),
                Pattern.compile("(?i)product:price:amount\"\\s*content=\"([0-9.,]+)\""),
                Pattern.compile("(?i)\"price\"\\s*:\\s*\"?([0-9.,]+)\"?"),
                Pattern.compile("(?i)\\$\\s*([0-9]{1,3}(?:\\.[0-9]{3})*(?:,[0-9]{1,2})?)")
        );

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                String candidate = matcher.group(1);
                BigDecimal parsed = parseMoney(candidate);
                if (parsed != null) {
                    return java.util.Optional.of(parsed);
                }
            }
        }

        return java.util.Optional.empty();
    }

    private BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim().replaceAll("\\s", "");
        int lastDot = normalized.lastIndexOf('.');
        int lastComma = normalized.lastIndexOf(',');

        try {
            if (lastDot >= 0 && lastComma >= 0) {
                if (lastComma > lastDot) {
                    normalized = normalized.replace(".", "").replace(",", ".");
                } else {
                    normalized = normalized.replace(",", "");
                }
            } else if (lastComma >= 0) {
                normalized = normalized.replace(".", "").replace(",", ".");
            } else {
                normalized = normalized.replace(",", "");
            }

            return new BigDecimal(normalized);
        } catch (Exception e) {
            log.debug("[Inventory-Monitor] Could not parse money value '{}': {}", raw, e.getMessage());
            return null;
        }
    }

    private Boolean detectAvailability(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }

        String lower = html.toLowerCase(Locale.ROOT);
        if (lower.contains("agotado") || lower.contains("sold out") || lower.contains("sin stock") || lower.contains("no disponible")) {
            return Boolean.FALSE;
        }

        return Boolean.TRUE;
    }
}
