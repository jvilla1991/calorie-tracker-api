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
    public List<FoodDto> search(@RequestParam String q) {
        return foodService.search(q);
    }

    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<FoodDto> byBarcode(@PathVariable String barcode) {
        return foodService.lookupBarcode(barcode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns the last {@code limit} distinct foods the user logged, most-recent first,
     * each paired with the quantity (grams) from their most recent log entry.
     * Used to power the "Recent" section of the food picker.
     */
    @GetMapping("/recent")
    public List<RecentFoodDto> recent(@RequestParam(defaultValue = "10") int limit) {
        return diaryService.getRecentFoods(Math.min(limit, 20));
    }

    @PostMapping
    public ResponseEntity<FoodDto> create(@Valid @RequestBody CreateFoodRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(foodService.create(req));
    }
}
