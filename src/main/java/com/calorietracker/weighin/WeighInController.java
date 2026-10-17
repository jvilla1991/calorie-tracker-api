package com.calorietracker.weighin;

import com.calorietracker.weighin.dto.WeighInRequest;
import com.calorietracker.weighin.dto.WeighInResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Body-weight log, one reading per day, always in kilograms (the frontend converts pounds).
 * Always requires authentication, GET included: weight is never part of the public read-only view.
 */
@RestController
@RequestMapping("/api/weigh-ins")
public class WeighInController {

    private final WeighInService weighInService;

    public WeighInController(WeighInService weighInService) {
        this.weighInService = weighInService;
    }

    /**
     * {@code GET /api/weigh-ins?from=YYYY-MM-DD&to=YYYY-MM-DD}: weigh-ins ascending by date.
     * Both params are optional and inclusive; omit both for the full history.
     * 400 if both are given and {@code from > to}.
     */
    @GetMapping
    public List<WeighInResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return weighInService.list(from, to);
    }

    /**
     * {@code POST /api/weigh-ins} with {@code {"date": "2026-10-16", "weightKg": 81.65}}.
     * Upserts by date: logging a date that already has a weigh-in replaces its weight.
     * Returns the saved row with <b>201 Created</b> when a new date was logged and
     * <b>200 OK</b> when an existing date's weight was replaced.
     * 400 if {@code date} is missing or in the future, or {@code weightKg} is missing or not in (0, 700).
     */
    @PostMapping
    public ResponseEntity<WeighInResponse> upsert(@Valid @RequestBody WeighInRequest req) {
        WeighInService.UpsertResult result = weighInService.upsert(req);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.weighIn());
    }

    /** {@code DELETE /api/weigh-ins/{id}}: 204 on success, 404 if no such weigh-in. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        weighInService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
