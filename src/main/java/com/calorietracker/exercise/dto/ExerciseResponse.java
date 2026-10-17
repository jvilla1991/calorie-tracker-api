package com.calorietracker.exercise.dto;

import com.calorietracker.exercise.ExerciseEntry;

/** A stored exercise entry: {@code {"id": 1, "name": "Morning run", "calories": 280}}. */
public record ExerciseResponse(Long id, String name, int calories) {

    public static ExerciseResponse from(ExerciseEntry e) {
        return new ExerciseResponse(e.getId(), e.getName(), e.getCalories());
    }
}
