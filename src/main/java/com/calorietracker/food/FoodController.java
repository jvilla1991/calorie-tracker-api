package com.calorietracker.food;

import com.calorietracker.diary.DiaryService;
import com.calorietracker.diary.dto.RecentFoodDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/foods")
public class FoodController {

    private final FoodService foodService;
    private final DiaryService diaryService;

    public FoodController(FoodService foodService, DiaryService diaryService) {
        this.foodService = foodService;
        this.diaryService = diaryService;
    }

    @GetMapping("/search")
    public FoodSearchResponse search(@RequestParam String q) {
        return foodService.search(q);
    }

    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<FoodDto> byBarcode(@PathVariable String barcode) {
        return foodService.lookupBarcode(barcode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns one page of distinct foods the user logged, most-recent first, each paired with
     * the quantity (grams) from its most recent log entry. Powers the "Recent" food picker.
     *
     * <p>{@code GET /api/foods/recent?page=0&size=20}. {@code page} defaults to 0 (negatives
     * clamp to 0); {@code size} defaults to 20 and is clamped to 1..50. The legacy
     * {@code limit} parameter is still accepted as an alias for {@code size} ({@code size}
     * wins if both are sent). The response is a plain JSON array; a page shorter than
     * {@code size} means there are no more results.
     */
    @GetMapping("/recent")
    public List<RecentFoodDto> recent(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(required = false) Integer size,
                                      @RequestParam(required = false) Integer limit) {
        int requested = size != null ? size
                : limit != null ? limit
                : DiaryService.RECENT_DEFAULT_SIZE;
        return diaryService.getRecentFoods(page, requested);
    }

    @PostMapping
    public ResponseEntity<FoodDto> create(@Valid @RequestBody CreateFoodRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(foodService.create(req));
    }
}
