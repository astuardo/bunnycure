package cl.bunnycure.service;

import cl.bunnycure.domain.model.Product;
import cl.bunnycure.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductPriceMonitoringService {

    private final ProductRepository productRepository;
    private final ProductPageScrapingService productPageScrapingService;

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

        ProductPageScrapingService.ProductScrapeResult scraped = productPageScrapingService.scrape(product.getPurchaseUrl());
        BigDecimal currentObservedPrice = product.getObservedPrice();
        BigDecimal observedPrice = scraped.observedPrice() != null ? scraped.observedPrice() : currentObservedPrice;
        Boolean observedAvailable = scraped.observedAvailable();

        if (observedPrice != null && (currentObservedPrice == null || observedPrice.compareTo(currentObservedPrice) != 0)) {
            product.setPreviousObservedPrice(currentObservedPrice);
            product.setObservedPrice(observedPrice);
        } else if (observedPrice == null) {
            product.setObservedPrice(currentObservedPrice);
        }
        product.setObservedAvailable(observedAvailable);
        product.setLastObservedAt(OffsetDateTime.now());

        Product saved = productRepository.save(product);
        log.info("[Inventory-Monitor] Refreshed product {} observedPrice={} observedAvailable={}",
                product.getId(), observedPrice, observedAvailable);
        return saved;
    }
}
