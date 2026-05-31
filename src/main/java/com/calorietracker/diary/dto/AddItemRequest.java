package com.calorietracker.diary.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class AddItemRequest {
    @NotNull private Long foodId;
    @NotNull @Positive private BigDecimal quantityGrams;

    public Long getFoodId() { return foodId; }
    public void setFoodId(Long id) { this.foodId = id; }
    public BigDecimal getQuantityGrams() { return quantityGrams; }
    public void setQuantityGrams(BigDecimal q) { this.quantityGrams = q; }
}
