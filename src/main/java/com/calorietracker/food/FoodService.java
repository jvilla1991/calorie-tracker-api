package com.calorietracker.food;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** Search local DB first; if empty, fetch from Open Food Facts and cache results */
    @Transactional
    public List<FoodDto> search(String query) {
        List<Food> local = foodRepository.searchByName(query);
        if (!local.isEmpty()) {
            log.debug("Food search '{}': {} local hits", query, local.size());
            return local.stream().map(FoodDto::from).toList();
        }

        log.debug("Food search '{}': querying Open Food Facts", query);
        List<OpenFoodFactsClient.OFFProduct> products = offClient.search(query);
        List<Food> saved = products.stream()
                .map(this::offProductToFood)
                .map(foodRepository::save)
                .toList();
        return saved.stream().map(FoodDto::from).toList();
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
