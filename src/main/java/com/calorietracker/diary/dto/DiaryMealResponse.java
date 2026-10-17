package com.calorietracker.diary.dto;

import com.calorietracker.diary.DiaryMeal;

import java.util.List;

/**
 * One meal in the diary. {@code note} and {@code estimatedCalories} are the meal's optional
 * details (null when not set). {@code totals} sums the logged items, and its {@code calories}
 * also includes {@code estimatedCalories}; an estimate adds no protein, carbs, fat or fiber.
 */
public record DiaryMealResponse(
        Long id,
        String mealType,
        int displayOrder,
        String note,
        Integer estimatedCalories,
        List<DiaryItemResponse> items,
        MacroTotals totals
) {
    public static DiaryMealResponse from(DiaryMeal meal) {
        List<DiaryItemResponse> items = meal.getItems().stream()
                .map(DiaryItemResponse::from).toList();
        MacroTotals totals = new MacroTotals();
        items.forEach(i -> totals.add(i.macros()));
        totals.add(MacroTotals.forEstimate(meal.getEstimatedCalories()));
        return new DiaryMealResponse(meal.getId(), meal.getMealType(), meal.getDisplayOrder(),
                meal.getNote(), meal.getEstimatedCalories(), items, totals);
    }
}
