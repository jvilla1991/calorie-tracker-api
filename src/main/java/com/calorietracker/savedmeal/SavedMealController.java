package com.calorietracker.savedmeal;

import com.calorietracker.savedmeal.dto.CreateSavedMealRequest;
import com.calorietracker.savedmeal.dto.SavedMealResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/saved-meals")
public class SavedMealController {

    private final SavedMealService savedMealService;

    public SavedMealController(SavedMealService savedMealService) {
        this.savedMealService = savedMealService;
    }

    @GetMapping
    public List<SavedMealResponse> list() {
        return savedMealService.listAll();
    }

    @PostMapping
    public ResponseEntity<SavedMealResponse> create(@Valid @RequestBody CreateSavedMealRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(savedMealService.create(req));
    }

    @PostMapping("/{id}/add-to-diary")
    public ResponseEntity<Void> addToDiary(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String mealType) {
        savedMealService.addToDiary(id, date, mealType);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        savedMealService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
