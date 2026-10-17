package com.calorietracker.eatingwindow.dto;

import com.calorietracker.eatingwindow.EatingWindow;

import java.time.Instant;

/**
 * A stored eating window. Times are instants serialized as ISO-8601 UTC strings, e.g.
 * {@code {"startedAt": "2026-10-16T16:20:00Z", "endedAt": null}}.
 */
public record EatingWindowResponse(Instant startedAt, Instant endedAt) {

    public static EatingWindowResponse from(EatingWindow w) {
        return new EatingWindowResponse(w.getStartedAt(), w.getEndedAt());
    }
}
