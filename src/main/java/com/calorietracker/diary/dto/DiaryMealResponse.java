package com.calorietracker.diary.dto;

import com.calorietracker.diary.DiaryMeal;

import java.util.List;

public record DiaryMealResponse(
        Long id,
        String mealType,
        int displayOrder,
        List<DiaryItemResponse> items,
        MacroTotals totals
) {
    public static DiaryMealResponse from(DiaryMeal meal) {
        List<DiaryItemResponse> items = meal.getItems().stream()
                .map(DiaryItemResponse::from).toList();
        MacroTotals totals = new MacroTotals();
        items.forEach(i -> totals.add(i.macros()));
        return new DiaryMealResponse(meal.getId(), meal.getMealType(),
                meal.getDisplayOrder(), items, totals);
    }
}
