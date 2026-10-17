package com.calorietracker.eatingwindow;

import com.calorietracker.eatingwindow.dto.EatingWindowRequest;
import com.calorietracker.eatingwindow.dto.EatingWindowResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Eating window (intermittent fasting log), part of the diary. Read back through
 * {@code GET /api/diary/{date}} ({@code eatingWindow}) and the summary
 * ({@code eatingStartedAt}/{@code eatingEndedAt}).
 */
@RestController
@RequestMapping("/api/diary")
public class EatingWindowController {

    private final EatingWindowService service;

    public EatingWindowController(EatingWindowService service) {
        this.service = service;
    }

    /**
     * {@code PUT /api/diary/{date}/eating-window} with
     * {@code {"startedAt": "2026-10-16T12:20:00-04:00", "endedAt": null}} → <b>200</b>
     * {@code {"startedAt": "2026-10-16T16:20:00Z", "endedAt": null}} (times returned in UTC).
     * Upserts by date. Both fields null is a 400: clear a day with DELETE instead.
     * See {@link EatingWindowService#upsert} for every 400 rule.
     */
    @PutMapping("/{date}/eating-window")
    public EatingWindowResponse upsert(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody EatingWindowRequest req) {
        return service.upsert(date, req);
    }

    /** {@code DELETE /api/diary/{date}/eating-window}: always 204, even if the day had none. */
    @DeleteMapping("/{date}/eating-window")
    public ResponseEntity<Void> delete(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        service.delete(date);
        return ResponseEntity.noContent().build();
    }
}
