package com.calorietracker.diary;

import com.calorietracker.diary.dto.*;
import com.calorietracker.eatingwindow.EatingWindowService;
import com.calorietracker.exercise.ExerciseService;
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

    /** Longest allowed meal note, counted after trimming. */
    public static final int NOTE_MAX_LENGTH = 500;

    private final DiaryEntryRepository entryRepo;
    private final DiaryMealRepository  mealRepo;
    private final DiaryItemRepository  itemRepo;
    private final FoodService          foodService;
    private final ExerciseService      exerciseService;
    private final EatingWindowService  eatingWindowService;

    public DiaryService(DiaryEntryRepository entryRepo,
                        DiaryMealRepository mealRepo,
                        DiaryItemRepository itemRepo,
                        FoodService foodService,
                        ExerciseService exerciseService,
                        EatingWindowService eatingWindowService) {
        this.entryRepo       = entryRepo;
        this.mealRepo        = mealRepo;
        this.itemRepo        = itemRepo;
        this.foodService     = foodService;
        this.exerciseService = exerciseService;
        this.eatingWindowService = eatingWindowService;
    }

    /**
     * The day view. Read-only: a day with no {@code diary_entries} row (e.g. exercise logged but
     * no food) is answered with empty meals and still carries its exercise and eating window;
     * no row is created.
     */
    @Transactional(readOnly = true)
    public DiaryResponse getDay(LocalDate date) {
        return entryRepo.findByEntryDate(date)
                .map(DiaryResponse::from)
                .orElse(DiaryResponse.empty(date))
                .withExercise(exerciseService.forDay(date))
                .withEatingWindow(eatingWindowService.forDay(date).orElse(null));
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

    /**
     * Replaces a meal's note and calorie estimate (both fields, every time: null clears one).
     * The note is trimmed and a blank note is stored as null. The estimate counts toward the
     * meal's and the day's calories.
     *
     * @throws ResponseStatusException 400 if the trimmed note is longer than
     *                                 {@value #NOTE_MAX_LENGTH} characters; 404 if no such meal
     */
    @Transactional
    public DiaryMealResponse updateDetails(Long mealId, MealDetailsRequest req) {
        String note = req.note() == null ? null : req.note().trim();
        if (note != null && note.isEmpty()) {
            note = null;
        }
        if (note != null && note.length() > NOTE_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Note must be at most " + NOTE_MAX_LENGTH + " characters");
        }
        DiaryMeal meal = mealRepo.findById(mealId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal not found"));
        meal.setNote(note);
        meal.setEstimatedCalories(req.estimatedCalories() == null ? null : req.estimatedCalories().intValueExact());
        return DiaryMealResponse.from(mealRepo.save(meal));
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

    /** Default and maximum page size for {@link #getRecentFoods(int, int)}. */
    public static final int RECENT_DEFAULT_SIZE = 20;
    public static final int RECENT_MAX_SIZE     = 50;

    /**
     * One page of distinct recently-logged foods, most recent first.
     * Out-of-range input is clamped rather than rejected: a negative {@code page} becomes 0,
     * and {@code size} is forced into {@code 1..RECENT_MAX_SIZE}.
     */
    @Transactional(readOnly = true)
    public List<RecentFoodDto> getRecentFoods(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), RECENT_MAX_SIZE);
        return itemRepo.findRecentDistinctFoods(PageRequest.of(safePage, safeSize))
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
