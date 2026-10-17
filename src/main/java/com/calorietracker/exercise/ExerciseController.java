package com.calorietracker.exercise;

import com.calorietracker.exercise.dto.ExerciseRequest;
import com.calorietracker.exercise.dto.ExerciseResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Exercise log, part of the diary. A day's entries are read back through
 * {@code GET /api/diary/{date}} ({@code exercise} and {@code exerciseCalories}).
 */
@RestController
@RequestMapping("/api/diary")
public class ExerciseController {

    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    /**
     * {@code POST /api/diary/{date}/exercise} with {@code {"name": "Morning run", "calories": 280}}
     * → <b>201</b> {@code {"id": 1, "name": "Morning run", "calories": 280}}.
     * 400 if the date is in the future, the trimmed name is not 1-60 characters, or calories is
     * missing or not a whole number in 1-5000.
     */
    @PostMapping("/{date}/exercise")
    public ResponseEntity<ExerciseResponse> add(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody ExerciseRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(exerciseService.add(date, req));
    }

    /** {@code DELETE /api/diary/exercise/{id}}: 204 on success, 404 if no such entry. */
    @DeleteMapping("/exercise/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        exerciseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
