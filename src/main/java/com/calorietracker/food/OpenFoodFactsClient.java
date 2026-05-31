package com.calorietracker.food;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class OpenFoodFactsClient {

    private static final String BASE = "https://world.openfoodfacts.org";
    private static final String UA  = "CalorieTracker/1.0 (github.com/jvilla1991/calorie-tracker-api)";

    private final RestClient restClient;

    public OpenFoodFactsClient() {
        this.restClient = RestClient.builder()
                .baseUrl(BASE)
                .defaultHeader("User-Agent", UA)
                .build();
    }

    /** Search by name — returns up to 10 products */
    public List<OFFProduct> search(String query) {
        try {
            OFFSearchResponse resp = restClient.get()
                    .uri("/cgi/search.pl?search_terms={q}&json=1&page_size=10&fields=code,product_name,brands,nutriments",
                            query)
                    .retrieve()
                    .body(OFFSearchResponse.class);
            return resp != null && resp.products != null ? resp.products : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** Look up by barcode */
    public Optional<OFFProduct> lookupBarcode(String barcode) {
        try {
            OFFBarcodeResponse resp = restClient.get()
                    .uri("/api/v2/product/{barcode}?fields=code,product_name,brands,nutriments", barcode)
                    .retrieve()
                    .body(OFFBarcodeResponse.class);
            if (resp != null && resp.status == 1 && resp.product != null) {
                return Optional.of(resp.product);
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    // ── inner response types ──────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OFFSearchResponse {
        public List<OFFProduct> products;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OFFBarcodeResponse {
        public int status;
        public OFFProduct product;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OFFProduct {
        public String code;
        @JsonProperty("product_name") public String productName;
        public String brands;
        public OFFNutriments nutriments;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OFFNutriments {
        @JsonProperty("energy-kcal_100g")   public BigDecimal caloriesPer100g;
        @JsonProperty("proteins_100g")      public BigDecimal proteinPer100g;
        @JsonProperty("carbohydrates_100g") public BigDecimal carbsPer100g;
        @JsonProperty("fat_100g")           public BigDecimal fatPer100g;
        @JsonProperty("fiber_100g")         public BigDecimal fiberPer100g;
    }
}
