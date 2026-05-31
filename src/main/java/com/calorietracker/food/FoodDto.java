package com.calorietracker.food;

import java.math.BigDecimal;

public record FoodDto(
        Long id,
        String barcode,
        String name,
        String brand,
        BigDecimal caloriesPer100g,
        BigDecimal proteinPer100g,
        BigDecimal carbsPer100g,
        BigDecimal fatPer100g,
        BigDecimal fiberPer100g,
        BigDecimal servingSizeG,
        String source
) {
    public static FoodDto from(Food f) {
        return new FoodDto(f.getId(), f.getBarcode(), f.getName(), f.getBrand(),
                f.getCaloriesPer100g(), f.getProteinPer100g(), f.getCarbsPer100g(),
                f.getFatPer100g(), f.getFiberPer100g(), f.getServingSizeG(), f.getSource());
    }
}
