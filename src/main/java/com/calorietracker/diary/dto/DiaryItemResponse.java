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
        MacroTotals m = MacroTotals.forItem(item);
        return new DiaryItemResponse(item.getId(), FoodDto.from(item.getFood()),
                item.getQuantityGrams(), m);
    }
}
