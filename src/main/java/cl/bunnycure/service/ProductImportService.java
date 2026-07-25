package cl.bunnycure.service;

import cl.bunnycure.web.dto.ProductImportPreviewDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductImportService {

    private final ProductPageScrapingService productPageScrapingService;

    public ProductImportPreviewDto previewFromUrl(String purchaseUrl) {
        ProductPageScrapingService.ProductScrapeResult scraped = productPageScrapingService.scrape(purchaseUrl);
        BigDecimal observedPrice = scraped.observedPrice();

        return ProductImportPreviewDto.builder()
                .name(scraped.productName())
                .purchaseUrl(scraped.normalizedUrl())
                .purchasePrice(observedPrice)
                .observedPrice(observedPrice)
                .observedAvailable(scraped.observedAvailable())
                .suggestedPurchaseUnit(scraped.suggestedPurchaseUnit())
                .suggestedConsumptionUnit(scraped.suggestedConsumptionUnit())
                .suggestedConversionFactor(scraped.suggestedConversionFactor())
                .build();
    }
}
