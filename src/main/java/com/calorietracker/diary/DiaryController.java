package com.calorietracker.diary;

import com.calorietracker.diary.dto.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/diary")
public class DiaryController {

    private final DiaryService diaryService;
    private final DiarySummaryService summaryService;

    public DiaryController(DiaryService diaryService, DiarySummaryService summaryService) {
        this.diaryService = diaryService;
        this.summaryService = summaryService;
    }

    /**
     * {@code GET /api/diary/summary?from=YYYY-MM-DD&to=YYYY-MM-DD}: one row per day,
     * {@code from} to {@code to} inclusive, ascending; unlogged days are zeros. Each row is
     * {@code {date, calories, protein, carbs, fat, fiber, goalCalories, exerciseCalories,
     * eatingStartedAt, eatingEndedAt}}.
     * 400 if a param is missing or malformed, {@code from > to}, or the range exceeds 366 days.
     */
    @GetMapping("/summary")
    public List<DailySummaryDto> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return summaryService.summarize(from, to);
    }

    @GetMapping("/{date}")
    public DiaryResponse getDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return diaryService.getDay(date);
    }

    @PostMapping("/{date}/meals")
    public ResponseEntity<DiaryMealResponse> addMeal(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody AddMealRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(diaryService.addMeal(date, req));
    }

    /**
     * {@code PUT /api/diary/meals/{mealId}/details} with
     * {@code {"note": "Pad thai at Lotus", "estimatedCalories": 900}} → <b>200</b> with the
     * updated meal (same shape as in {@code GET /api/diary/{date}}; its {@code totals.calories}
     * includes the estimate). Both fields are replaced, so send both: null or missing clears one.
     * 400 if the trimmed note is over 500 characters or {@code estimatedCalories} is not a whole
     * number in 1-10000; 404 if no such meal.
     */
    @PutMapping("/meals/{mealId}/details")
    public DiaryMealResponse updateDetails(
            @PathVariable Long mealId,
            @Valid @RequestBody MealDetailsRequest req) {
        return diaryService.updateDetails(mealId, req);
    }

    @PostMapping("/meals/{mealId}/items")
    public ResponseEntity<DiaryItemResponse> addItem(
            @PathVariable Long mealId,
            @Valid @RequestBody AddItemRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(diaryService.addItem(mealId, req));
    }

    @PutMapping("/meals/items/{itemId}")
    public DiaryItemResponse updateItem(
            @PathVariable Long itemId,
            @RequestBody Map<String, BigDecimal> body) {
        return diaryService.updateItem(itemId, body.get("quantityGrams"));
    }

    @DeleteMapping("/meals/items/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long itemId) {
        diaryService.deleteItem(itemId);
        return ResponseEntity.noContent().build();
    }
}
