package com.calorietracker.savedmeal;

import com.calorietracker.diary.DiaryService;
import com.calorietracker.diary.dto.AddItemRequest;
import com.calorietracker.diary.dto.AddMealRequest;
import com.calorietracker.food.Food;
import com.calorietracker.food.FoodService;
import com.calorietracker.savedmeal.dto.CreateSavedMealRequest;
import com.calorietracker.savedmeal.dto.SavedMealResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class SavedMealService {

    private final SavedMealRepository savedMealRepo;
    private final FoodService          foodService;
    private final DiaryService         diaryService;

    public SavedMealService(SavedMealRepository savedMealRepo,
                             FoodService foodService,
                             DiaryService diaryService) {
        this.savedMealRepo = savedMealRepo;
        this.foodService   = foodService;
        this.diaryService  = diaryService;
    }

    @Transactional(readOnly = true)
    public List<SavedMealResponse> listAll() {
        return savedMealRepo.findAll().stream().map(SavedMealResponse::from).toList();
    }

    @Transactional
    public SavedMealResponse create(CreateSavedMealRequest req) {
        SavedMeal meal = new SavedMeal();
        meal.setName(req.getName());

        List<SavedMealItem> items = req.getItems().stream().map(ir -> {
            Food food = foodService.findById(ir.getFoodId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Food not found: " + ir.getFoodId()));
            SavedMealItem item = new SavedMealItem();
            item.setSavedMeal(meal);
            item.setFood(food);
            item.setQuantityGrams(ir.getQuantityGrams());
            return item;
        }).toList();

        meal.getItems().addAll(items);
        return SavedMealResponse.from(savedMealRepo.save(meal));
    }

    @Transactional
    public void delete(Long id) {
        if (!savedMealRepo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved meal not found");
        }
        savedMealRepo.deleteById(id);
    }

    /** Quick-add: create a new diary meal from the saved meal's items */
    @Transactional
    public void addToDiary(Long savedMealId, LocalDate date, String mealType) {
        SavedMeal saved = savedMealRepo.findById(savedMealId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved meal not found"));

        AddMealRequest mealReq = new AddMealRequest();
        mealReq.setMealType(mealType);
        var mealResp = diaryService.addMeal(date, mealReq);

        saved.getItems().forEach(si -> {
            AddItemRequest itemReq = new AddItemRequest();
            itemReq.setFoodId(si.getFood().getId());
            itemReq.setQuantityGrams(si.getQuantityGrams());
            diaryService.addItem(mealResp.id(), itemReq);
        });
    }
}
