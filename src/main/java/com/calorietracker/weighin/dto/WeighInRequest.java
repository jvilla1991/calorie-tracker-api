package com.calorietracker.weighin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Body of POST /api/weigh-ins. {@code weightKg} is always kilograms, exclusive range (0, 700),
 * stored to 3 decimal places. {@code date} is required and may not be in the future
 * (judged by the server's clock).
 */
public record WeighInRequest(
        @NotNull @PastOrPresent LocalDate date,
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax(value = "700", inclusive = false)
        BigDecimal weightKg
) {}
