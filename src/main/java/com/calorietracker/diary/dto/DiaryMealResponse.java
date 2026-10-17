package com.calorietracker.diary.dto;

import com.calorietracker.diary.DiaryMeal;
import com.calorietracker.photo.dto.MealPhotoResponse;

import java.util.List;

/**
 * One meal in the diary. {@code note} and {@code estimatedCalories} are the meal's optional
 * details (null when not set). {@code totals} sums the logged items, and its {@code calories}
 * also includes {@code estimatedCalories}; an estimate adds no protein, carbs, fat or fiber.
 * {@code photos} lists the meal's photos oldest first (empty when none).
 */
public record DiaryMealResponse(
        Long id,
        String mealType,
        int displayOrder,
        String note,
        Integer estimatedCalories,
        List<DiaryItemResponse> items,
        MacroTotals totals,
        List<MealPhotoResponse> photos
) {
    /** The meal with no photos attached; see {@link #withPhotos}. */
    public static DiaryMealResponse from(DiaryMeal meal) {
        List<DiaryItemResponse> items = meal.getItems().stream()
                .map(DiaryItemResponse::from).toList();
        MacroTotals totals = new MacroTotals();
        items.forEach(i -> totals.add(i.macros()));
        totals.add(MacroTotals.forEstimate(meal.getEstimatedCalories()));
        return new DiaryMealResponse(meal.getId(), meal.getMealType(), meal.getDisplayOrder(),
                meal.getNote(), meal.getEstimatedCalories(), items, totals, List.of());
    }

    /** A copy with the meal's photos attached (oldest first). */
    public DiaryMealResponse withPhotos(List<MealPhotoResponse> photos) {
        return new DiaryMealResponse(id, mealType, displayOrder, note, estimatedCalories, items, totals,
                List.copyOf(photos));
    }
}
