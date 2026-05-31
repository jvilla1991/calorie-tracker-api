package com.calorietracker.diary.dto;

import com.calorietracker.diary.DiaryItem;
import com.calorietracker.food.FoodDto;

import java.math.BigDecimal;

/**
 * The most-recent log entry for a given food, returned by GET /api/foods/recent.
 * {@code quantityGrams} is always in grams (the canonical storage unit).
 * {@code addedAt} is an ISO-8601 offset string, e.g. "2026-05-31T14:22:00Z".
 */
public record RecentFoodDto(FoodDto food, BigDecimal quantityGrams, String addedAt) {

    public static RecentFoodDto from(DiaryItem item) {
        return new RecentFoodDto(
                FoodDto.from(item.getFood()),
                item.getQuantityGrams(),
                item.getAddedAt().toString()
        );
    }
}
