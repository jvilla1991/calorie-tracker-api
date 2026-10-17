package com.calorietracker.diary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day's row in GET /api/diary/summary. Macro values use the same math and numeric style
 * as {@link MacroTotals} on the diary day view; {@code goalCalories} is the calories goal that
 * was in effect on {@code date}. {@code exerciseCalories} is the day's total calories burned
 * by logged exercise (0 when none); {@code calories} stays food-only.
 */
public record DailySummaryDto(
        LocalDate date,
        BigDecimal calories,
        BigDecimal protein,
        BigDecimal carbs,
        BigDecimal fat,
        BigDecimal fiber,
        int goalCalories,
        int exerciseCalories
) {
    public static DailySummaryDto of(LocalDate date, MacroTotals t, int goalCalories,
                                     int exerciseCalories) {
        return new DailySummaryDto(date, t.getCalories(), t.getProtein(), t.getCarbs(),
                t.getFat(), t.getFiber(), goalCalories, exerciseCalories);
    }
}
