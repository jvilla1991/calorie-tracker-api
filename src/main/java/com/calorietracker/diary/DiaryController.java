package com.calorietracker.diary;

import com.calorietracker.diary.dto.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/diary")
public class DiaryController {

    private final DiaryService diaryService;

    public DiaryController(DiaryService diaryService) {
        this.diaryService = diaryService;
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
