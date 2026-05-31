package com.calorietracker.diary.dto;

import com.calorietracker.diary.DiaryEntry;

import java.time.LocalDate;
import java.util.List;

public record DiaryResponse(
        LocalDate date,
        List<DiaryMealResponse> meals,
        MacroTotals totals
) {
    public static DiaryResponse from(DiaryEntry entry) {
        List<DiaryMealResponse> meals = entry.getMeals().stream()
                .map(DiaryMealResponse::from).toList();
        MacroTotals totals = new MacroTotals();
        meals.forEach(m -> totals.add(m.totals()));
        return new DiaryResponse(entry.getEntryDate(), meals, totals);
    }

    public static DiaryResponse empty(LocalDate date) {
        return new DiaryResponse(date, List.of(), new MacroTotals());
    }
}
