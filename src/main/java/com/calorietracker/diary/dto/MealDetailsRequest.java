package com.calorietracker.diary.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

/**
 * Body of PUT /api/diary/meals/{mealId}/details:
 * {@code {"note": "Pad thai at Lotus", "estimatedCalories": 900}}.
 * <p>
 * Both fields are replaced on every call, so send both: a missing or null field clears it.
 * {@code note} is trimmed, a blank note is stored as null, and the trimmed note must be at most
 * 500 characters (checked in the service, since the rule applies after trimming).
 * {@code estimatedCalories} must be a whole number in 1-10000; it is bound as a decimal so that a
 * fractional value such as {@code 900.5} is rejected with 400 instead of being silently truncated.
 */
public record MealDetailsRequest(
        String note,
        @Digits(integer = 5, fraction = 0)
        @DecimalMin("1")
        @DecimalMax("10000")
        BigDecimal estimatedCalories
) {}
