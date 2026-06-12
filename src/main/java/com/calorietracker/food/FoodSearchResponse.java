package com.calorietracker.food;

import java.util.List;

/**
 * Grouped result of GET /api/foods/search.
 *
 * <ul>
 *   <li>{@code mine} — foods the user created themselves (source = "custom").</li>
 *   <li>{@code external} — matches from the external Open Food Facts database.</li>
 * </ul>
 *
 * The two lists are independent: a search always returns the user's own matching
 * foods <em>and</em> external matches, so neither category hides the other.
 */
public record FoodSearchResponse(List<FoodDto> mine, List<FoodDto> external) {
}
