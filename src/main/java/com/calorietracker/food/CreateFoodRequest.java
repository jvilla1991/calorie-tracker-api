package com.calorietracker.food;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class CreateFoodRequest {

    private String barcode;

    @NotBlank
    private String name;

    private String brand;

    @NotNull
    private BigDecimal caloriesPer100g;

    // All macro fields are optional — not every food label includes all values
    private BigDecimal proteinPer100g;
    private BigDecimal carbsPer100g;
    private BigDecimal fatPer100g;
    private BigDecimal fiberPer100g;

    /**
     * Reference weight (grams) for one standard serving.
     * When present, clients can log food in "servings" (quantity × servingSizeG = grams logged).
     * All nutrition values are still stored per 100g.
     */
    private BigDecimal servingSizeG;

    public String getBarcode() { return barcode; }
    public void setBarcode(String b) { this.barcode = b; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public String getBrand() { return brand; }
    public void setBrand(String b) { this.brand = b; }
    public BigDecimal getCaloriesPer100g() { return caloriesPer100g; }
    public void setCaloriesPer100g(BigDecimal v) { this.caloriesPer100g = v; }
    public BigDecimal getProteinPer100g() { return proteinPer100g; }
    public void setProteinPer100g(BigDecimal v) { this.proteinPer100g = v; }
    public BigDecimal getCarbsPer100g() { return carbsPer100g; }
    public void setCarbsPer100g(BigDecimal v) { this.carbsPer100g = v; }
    public BigDecimal getFatPer100g() { return fatPer100g; }
    public void setFatPer100g(BigDecimal v) { this.fatPer100g = v; }
    public BigDecimal getFiberPer100g() { return fiberPer100g; }
    public void setFiberPer100g(BigDecimal v) { this.fiberPer100g = v; }
    public BigDecimal getServingSizeG() { return servingSizeG; }
    public void setServingSizeG(BigDecimal v) { this.servingSizeG = v; }
}
