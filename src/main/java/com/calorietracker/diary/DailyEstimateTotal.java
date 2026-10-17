package com.calorietracker.diary;

import java.time.LocalDate;

/** Sum of one day's meal calorie estimates (projection of {@link DiaryMealRepository#sumEstimatedCaloriesByDate}). */
public record DailyEstimateTotal(LocalDate date, Long calories) {}
