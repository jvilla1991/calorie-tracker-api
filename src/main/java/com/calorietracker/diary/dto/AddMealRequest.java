package com.calorietracker.diary.dto;

import jakarta.validation.constraints.NotBlank;

public class AddMealRequest {
    @NotBlank
    private String mealType;

    public String getMealType() { return mealType; }
    public void setMealType(String t) { this.mealType = t; }
}
