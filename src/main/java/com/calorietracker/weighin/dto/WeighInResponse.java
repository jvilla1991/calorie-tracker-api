package com.calorietracker.weighin.dto;

import com.calorietracker.weighin.WeighIn;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A stored weigh-in: {@code {"id": 1, "date": "2026-10-16", "weightKg": 81.650}}. */
public record WeighInResponse(Long id, LocalDate date, BigDecimal weightKg) {

    public static WeighInResponse from(WeighIn w) {
        return new WeighInResponse(w.getId(), w.getWeighDate(), w.getWeightKg());
    }
}
