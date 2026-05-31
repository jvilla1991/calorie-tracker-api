package com.calorietracker.diary.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MacroTotals {
    private BigDecimal calories = BigDecimal.ZERO;
    private BigDecimal protein  = BigDecimal.ZERO;
    private BigDecimal carbs    = BigDecimal.ZERO;
    private BigDecimal fat      = BigDecimal.ZERO;
    private BigDecimal fiber    = BigDecimal.ZERO;

    public void add(MacroTotals other) {
        this.calories = this.calories.add(other.calories);
        this.protein  = this.protein.add(other.protein);
        this.carbs    = this.carbs.add(other.carbs);
        this.fat      = this.fat.add(other.fat);
        this.fiber    = this.fiber.add(other.fiber);
    }

    public static MacroTotals scale(BigDecimal per100g_cal, BigDecimal per100g_prot,
                                     BigDecimal per100g_carb, BigDecimal per100g_fat,
                                     BigDecimal per100g_fib, BigDecimal grams) {
        MacroTotals t = new MacroTotals();
        t.calories = scale(per100g_cal, grams);
        t.protein  = scale(per100g_prot, grams);
        t.carbs    = scale(per100g_carb, grams);
        t.fat      = scale(per100g_fat, grams);
        t.fiber    = scale(per100g_fib, grams);
        return t;
    }

    private static BigDecimal scale(BigDecimal per100g, BigDecimal grams) {
        if (per100g == null) return BigDecimal.ZERO;
        return per100g.multiply(grams)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getCalories() { return calories; }
    public BigDecimal getProtein()  { return protein; }
    public BigDecimal getCarbs()    { return carbs; }
    public BigDecimal getFat()      { return fat; }
    public BigDecimal getFiber()    { return fiber; }
}
