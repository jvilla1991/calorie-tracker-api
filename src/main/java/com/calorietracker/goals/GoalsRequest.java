package com.calorietracker.goals;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class GoalsRequest {
    @NotNull @Positive private Integer calories;
    @NotNull @Positive private BigDecimal protein;
    @NotNull @Positive private BigDecimal carbs;
    @NotNull @Positive private BigDecimal fat;
    @NotNull @Positive private BigDecimal fiber;

    public Integer getCalories() { return calories; }
    public void setCalories(Integer c) { this.calories = c; }
    public BigDecimal getProtein() { return protein; }
    public void setProtein(BigDecimal p) { this.protein = p; }
    public BigDecimal getCarbs() { return carbs; }
    public void setCarbs(BigDecimal c) { this.carbs = c; }
    public BigDecimal getFat() { return fat; }
    public void setFat(BigDecimal f) { this.fat = f; }
    public BigDecimal getFiber() { return fiber; }
    public void setFiber(BigDecimal f) { this.fiber = f; }
}
