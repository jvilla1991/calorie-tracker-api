package com.calorietracker.diary.dto;

import com.calorietracker.diary.DiaryItem;
import com.calorietracker.food.FoodDto;

import java.math.BigDecimal;

public record DiaryItemResponse(
        Long id,
        FoodDto food,
        BigDecimal quantityGrams,
        MacroTotals macros
) {
    public static DiaryItemResponse from(DiaryItem item) {
        MacroTotals m = MacroTotals.scale(
                item.getFood().getCaloriesPer100g(),
                item.getFood().getProteinPer100g(),
                item.getFood().getCarbsPer100g(),
                item.getFood().getFatPer100g(),
                item.getFood().getFiberPer100g(),
                item.getQuantityGrams());
        return new DiaryItemResponse(item.getId(), FoodDto.from(item.getFood()),
                item.getQuantityGrams(), m);
    }
}
