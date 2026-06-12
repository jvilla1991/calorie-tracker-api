package com.calorietracker.food;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Service
public class FoodService {

    private static final Logger log = LoggerFactory.getLogger(FoodService.class);

    private final FoodRepository foodRepository;
    private final OpenFoodFactsClient offClient;

    public FoodService(FoodRepository foodRepository, OpenFoodFactsClient offClient) {
        this.foodRepository = foodRepository;
        this.offClient = offClient;
    }

    /**
     * Search foods in two independent categories, always returned together:
     * <ul>
     *   <li>{@code mine} — the user's own foods (source = "custom").</li>
     *   <li>{@code external} — Open Food Facts matches: previously-cached ones plus a
     *       fresh API query, so results persist even if Open Food Facts is unreachable.</li>
     * </ul>
     * External results are deduped (by barcode, else name+brand) and cached without
     * inserting duplicate rows when the same product is seen again.
     */
    @Transactional
    public FoodSearchResponse search(String query) {
        List<FoodDto> mine = foodRepository.searchByNameAndSource(query, "custom")
                .stream().map(FoodDto::from).toList();

        // Dedup external matches in first-seen order: cached rows first, then fresh API hits.
        LinkedHashMap<String, Food> external = new LinkedHashMap<>();
        for (Food f : foodRepository.searchByNameAndSource(query, "openfoodfacts")) {
            external.putIfAbsent(dedupKey(f), f);
        }
        for (OpenFoodFactsClient.OFFProduct p : offClient.search(query)) {
            Food f = upsertExternal(p);
            external.putIfAbsent(dedupKey(f), f);
        }

        log.debug("Food search '{}': {} mine, {} external", query, mine.size(), external.size());
        List<FoodDto> externalDtos = external.values().stream().map(FoodDto::from).toList();
        return new FoodSearchResponse(mine, externalDtos);
    }

    /** Cache an Open Food Facts product, reusing the existing row if its barcode is already stored. */
    private Food upsertExternal(OpenFoodFactsClient.OFFProduct p) {
        if (p.code != null && !p.code.isBlank()) {
            Optional<Food> existing = foodRepository.findByBarcode(p.code);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        return foodRepository.save(offProductToFood(p));
    }

    /** Stable identity for an external food: barcode when present, otherwise name+brand. */
    private static String dedupKey(Food f) {
        if (f.getBarcode() != null && !f.getBarcode().isBlank()) {
            return "bc:" + f.getBarcode();
        }
        String name  = f.getName()  == null ? "" : f.getName().toLowerCase();
        String brand = f.getBrand() == null ? "" : f.getBrand().toLowerCase();
        return "nm:" + name + "|" + brand;
    }

    /** Barcode lookup: local first, then Open Food Facts with auto-cache */
    @Transactional
    public Optional<FoodDto> lookupBarcode(String barcode) {
        Optional<Food> local = foodRepository.findByBarcode(barcode);
        if (local.isPresent()) {
            log.debug("Barcode {}: local cache hit", barcode);
            return local.map(FoodDto::from);
        }

        log.debug("Barcode {}: querying Open Food Facts", barcode);
        return offClient.lookupBarcode(barcode)
                .map(this::offProductToFood)
                .map(foodRepository::save)
                .map(FoodDto::from);
    }

    @Transactional
    public FoodDto create(CreateFoodRequest req) {
        Food food = new Food();
        food.setBarcode(req.getBarcode());
        food.setName(req.getName());
        food.setBrand(req.getBrand());
        food.setCaloriesPer100g(req.getCaloriesPer100g());
        food.setProteinPer100g(req.getProteinPer100g());
        food.setCarbsPer100g(req.getCarbsPer100g());
        food.setFatPer100g(req.getFatPer100g());
        food.setFiberPer100g(req.getFiberPer100g());
        food.setServingSizeG(req.getServingSizeG());
        food.setSource("custom");
        return FoodDto.from(foodRepository.save(food));
    }

    public Optional<Food> findById(Long id) {
        return foodRepository.findById(id);
    }

    private Food offProductToFood(OpenFoodFactsClient.OFFProduct p) {
        Food food = new Food();
        food.setBarcode(p.code);
        food.setName(p.productName != null ? p.productName : "Unknown");
        food.setBrand(p.brands);
        food.setSource("openfoodfacts");
        if (p.nutriments != null) {
            food.setCaloriesPer100g(p.nutriments.caloriesPer100g);
            food.setProteinPer100g(p.nutriments.proteinPer100g);
            food.setCarbsPer100g(p.nutriments.carbsPer100g);
            food.setFatPer100g(p.nutriments.fatPer100g);
            food.setFiberPer100g(p.nutriments.fiberPer100g);
        }
        return food;
    }
}
