package com.calorietracker.exercise;

import java.time.LocalDate;

/** Total calories burned on one day (projection of {@link ExerciseEntryRepository#sumCaloriesByDate}). */
public record DailyExerciseTotal(LocalDate date, Long calories) {}
