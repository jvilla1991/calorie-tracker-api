package com.calorietracker.diary;

import com.calorietracker.diary.dto.*;
import com.calorietracker.food.Food;
import com.calorietracker.food.FoodService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class DiaryService {

    private final DiaryEntryRepository entryRepo;
    private final DiaryMealRepository  mealRepo;
    private final DiaryItemRepository  itemRepo;
    private final FoodService          foodService;

    public DiaryService(DiaryEntryRepository entryRepo,
                        DiaryMealRepository mealRepo,
                        DiaryItemRepository itemRepo,
                        FoodService foodService) {
        this.entryRepo   = entryRepo;
        this.mealRepo    = mealRepo;
        this.itemRepo    = itemRepo;
        this.foodService = foodService;
    }

    @Transactional(readOnly = true)
    public DiaryResponse getDay(LocalDate date) {
        return entryRepo.findByEntryDate(date)
                .map(DiaryResponse::from)
                .orElse(DiaryResponse.empty(date));
    }

    @Transactional
    public DiaryMealResponse addMeal(LocalDate date, AddMealRequest req) {
        DiaryEntry entry = entryRepo.findByEntryDate(date)
                .orElseGet(() -> {
                    DiaryEntry e = new DiaryEntry();
                    e.setEntryDate(date);
                    return entryRepo.save(e);
                });

        int order = entry.getMeals().size();
        DiaryMeal meal = new DiaryMeal();
        meal.setDiaryEntry(entry);
        meal.setMealType(req.getMealType().toLowerCase());
        meal.setDisplayOrder(order);
        meal = mealRepo.save(meal);
        return DiaryMealResponse.from(meal);
    }

    @Transactional
    public DiaryItemResponse addItem(Long mealId, AddItemRequest req) {
        DiaryMeal meal = mealRepo.findById(mealId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal not found"));
        Food food = foodService.findById(req.getFoodId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food not found"));

        DiaryItem item = new DiaryItem();
        item.setDiaryMeal(meal);
        item.setFood(food);
        item.setQuantityGrams(req.getQuantityGrams());
        item = itemRepo.save(item);
        return DiaryItemResponse.from(item);
    }

    @Transactional
    public DiaryItemResponse updateItem(Long itemId, BigDecimal newGrams) {
        DiaryItem item = itemRepo.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        item.setQuantityGrams(newGrams);
        return DiaryItemResponse.from(itemRepo.save(item));
    }

    @Transactional(readOnly = true)
    public List<RecentFoodDto> getRecentFoods(int limit) {
        return itemRepo.findRecentDistinctFoods(PageRequest.of(0, limit))
                .stream()
                .map(RecentFoodDto::from)
                .toList();
    }

    @Transactional
    public void deleteItem(Long itemId) {
        if (!itemRepo.existsById(itemId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
        }
        itemRepo.deleteById(itemId);
    }
}
