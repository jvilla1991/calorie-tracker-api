package com.calorietracker.diary.dto;

import com.calorietracker.diary.DiaryEntry;
import com.calorietracker.eatingwindow.dto.EatingWindowResponse;
import com.calorietracker.exercise.dto.ExerciseResponse;
import com.calorietracker.photo.dto.MealPhotoResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * GET /api/diary/{date}. {@code totals} covers food only: the logged items plus the meals'
 * calorie estimates (estimates add calories, no macros). {@code exercise} lists the day's
 * exercise entries (id ascending, empty when none) and {@code exerciseCalories} is their sum.
 * {@code eatingWindow} is the day's eating window, or {@code null} when none was logged.
 * Each meal carries its {@code photos}.
 */
public record DiaryResponse(
        LocalDate date,
        List<DiaryMealResponse> meals,
        MacroTotals totals,
        List<ExerciseResponse> exercise,
        int exerciseCalories,
        EatingWindowResponse eatingWindow
) {
    public static DiaryResponse from(DiaryEntry entry) {
        List<DiaryMealResponse> meals = entry.getMeals().stream()
                .map(DiaryMealResponse::from).toList();
        MacroTotals totals = new MacroTotals();
        meals.forEach(m -> totals.add(m.totals()));
        return new DiaryResponse(entry.getEntryDate(), meals, totals, List.of(), 0, null);
    }

    public static DiaryResponse empty(LocalDate date) {
        return new DiaryResponse(date, List.of(), new MacroTotals(), List.of(), 0, null);
    }

    /**
     * A copy with each meal's photos attached, looked up by meal id in {@code photosByMealId}
     * (a meal with no entry gets an empty list).
     */
    public DiaryResponse withPhotos(Map<Long, List<MealPhotoResponse>> photosByMealId) {
        List<DiaryMealResponse> withPhotos = meals.stream()
                .map(m -> m.withPhotos(photosByMealId.getOrDefault(m.id(), List.of())))
                .toList();
        return new DiaryResponse(date, withPhotos, totals, exercise, exerciseCalories, eatingWindow);
    }

    /** A copy with the day's exercise entries attached and {@code exerciseCalories} summed. */
    public DiaryResponse withExercise(List<ExerciseResponse> entries) {
        int sum = entries.stream().mapToInt(ExerciseResponse::calories).sum();
        return new DiaryResponse(date, meals, totals, List.copyOf(entries), sum, eatingWindow);
    }

    /** A copy with the day's eating window attached ({@code null} for none). */
    public DiaryResponse withEatingWindow(EatingWindowResponse window) {
        return new DiaryResponse(date, meals, totals, exercise, exerciseCalories, window);
    }
}
