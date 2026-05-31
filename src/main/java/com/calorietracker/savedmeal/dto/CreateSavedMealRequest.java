package com.calorietracker.savedmeal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class CreateSavedMealRequest {
    @NotBlank private String name;
    @NotEmpty @Valid private List<SavedMealItemRequest> items;

    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public List<SavedMealItemRequest> getItems() { return items; }
    public void setItems(List<SavedMealItemRequest> i) { this.items = i; }
}
