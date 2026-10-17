package com.calorietracker.eatingwindow.dto;

import java.time.OffsetDateTime;

/**
 * Body of PUT /api/diary/{date}/eating-window:
 * {@code {"startedAt": "2026-10-16T12:20:00-04:00", "endedAt": null}}.
 * Both are ISO-8601 date-times <b>with</b> an offset (a value without one is a 400); either
 * may be null, but not both. The rules are enforced in {@code EatingWindowService#upsert}.
 */
public record EatingWindowRequest(OffsetDateTime startedAt, OffsetDateTime endedAt) {}
