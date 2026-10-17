package com.calorietracker.exercise.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Body of POST /api/diary/{date}/exercise: {@code {"name": "Morning run", "calories": 280}}.
 * {@code name} is trimmed and must then be 1-60 characters (checked in the service, since the
 * length rule applies after trimming). {@code calories} must be a whole number in 1-5000;
 * it is bound as a decimal so that a fractional value such as {@code 280.5} is rejected with
 * 400 instead of being silently truncated by JSON number coercion.
 */
public record ExerciseRequest(
        String name,
        @NotNull
        @Digits(integer = 4, fraction = 0)
        @DecimalMin("1")
        @DecimalMax("5000")
        BigDecimal calories
) {}
