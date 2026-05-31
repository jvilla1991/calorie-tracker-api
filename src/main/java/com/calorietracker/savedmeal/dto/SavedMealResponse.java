package com.calorietracker.savedmeal.dto;

import com.calorietracker.diary.dto.MacroTotals;
import com.calorietracker.food.FoodDto;
import com.calorietracker.savedmeal.SavedMeal;
import com.calorietracker.savedmeal.SavedMealItem;

import java.math.BigDecimal;
import java.util.List;

public record SavedMealResponse(
        Long id,
        String name,
        List<SavedMealItemResponse> items,
        MacroTotals totals
) {
    public record SavedMealItemResponse(FoodDto food, BigDecimal quantityGrams, MacroTotals macros) {}

    public static SavedMealResponse from(SavedMeal meal) {
        List<SavedMealItemResponse> items = meal.getItems().stream()
                .map(i -> {
                    MacroTotals m = MacroTotals.scale(
                            i.getFood().getCaloriesPer100g(),
                            i.getFood().getProteinPer100g(),
                            i.getFood().getCarbsPer100g(),
                            i.getFood().getFatPer100g(),
                            i.getFood().getFiberPer100g(),
                            i.getQuantityGrams());
                    return new SavedMealItemResponse(FoodDto.from(i.getFood()), i.getQuantityGrams(), m);
                }).toList();
        MacroTotals totals = new MacroTotals();
        items.forEach(i -> totals.add(i.macros()));
        return new SavedMealResponse(meal.getId(), meal.getName(), items, totals);
    }
}
