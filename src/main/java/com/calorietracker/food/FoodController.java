package com.calorietracker.food;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/foods")
public class FoodController {

    private final FoodService foodService;

    public FoodController(FoodService foodService) {
        this.foodService = foodService;
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

    @PostMapping
    public ResponseEntity<FoodDto> create(@Valid @RequestBody CreateFoodRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(foodService.create(req));
    }
}
