package com.calorietracker.diary;

import com.calorietracker.diary.dto.DailySummaryDto;
import com.calorietracker.diary.dto.MacroTotals;
import com.calorietracker.eatingwindow.EatingWindowService;
import com.calorietracker.eatingwindow.dto.EatingWindowResponse;
import com.calorietracker.exercise.ExerciseService;
import com.calorietracker.goals.GoalsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-day nutrition totals over a date range, for trend charts.
 * Uses five queries total (items in range + meal calorie estimates in range + goal history +
 * exercise totals + eating windows), regardless of range length.
 */
@Service
public class DiarySummaryService {

    /** Longest allowed range, counted inclusively (a full leap year). */
    public static final int MAX_RANGE_DAYS = 366;

    private final DiaryItemRepository itemRepo;
    private final DiaryMealRepository mealRepo;
    private final GoalsService goalsService;
    private final ExerciseService exerciseService;
    private final EatingWindowService eatingWindowService;

    public DiarySummaryService(DiaryItemRepository itemRepo, DiaryMealRepository mealRepo,
                               GoalsService goalsService, ExerciseService exerciseService,
                               EatingWindowService eatingWindowService) {
        this.itemRepo = itemRepo;
        this.mealRepo = mealRepo;
        this.goalsService = goalsService;
        this.exerciseService = exerciseService;
        this.eatingWindowService = eatingWindowService;
    }

    /**
     * One row per day from {@code from} to {@code to} inclusive, ascending. Days with nothing
     * logged are present with zero totals. A day's {@code calories} includes its meals' calorie
     * estimates, exactly as the diary day view's totals do.
     *
     * @throws ResponseStatusException 400 if {@code from > to} or the range exceeds
     *                                 {@value #MAX_RANGE_DAYS} days
     */
    @Transactional(readOnly = true)
    public List<DailySummaryDto> summarize(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Range must be at most " + MAX_RANGE_DAYS + " days");
        }

        Map<LocalDate, MacroTotals> totalsByDate = new HashMap<>();
        for (DiaryItem item : itemRepo.findAllInDateRange(from, to)) {
            LocalDate date = item.getDiaryMeal().getDiaryEntry().getEntryDate();
            totalsByDate.computeIfAbsent(date, d -> new MacroTotals()).add(MacroTotals.forItem(item));
        }
        for (DailyEstimateTotal estimate : mealRepo.sumEstimatedCaloriesByDate(from, to)) {
            totalsByDate.computeIfAbsent(estimate.date(), d -> new MacroTotals())
                    .add(MacroTotals.forEstimate(Math.toIntExact(estimate.calories())));
        }
        Map<LocalDate, Integer> goals = goalsService.caloriesGoalsByDate(from, to);
        Map<LocalDate, Integer> exercise = exerciseService.caloriesByDate(from, to);
        Map<LocalDate, EatingWindowResponse> windows = eatingWindowService.byDate(from, to);

        List<DailySummaryDto> rows = new ArrayList<>((int) days);
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            rows.add(DailySummaryDto.of(d, totalsByDate.getOrDefault(d, new MacroTotals()), goals.get(d),
                    exercise.getOrDefault(d, 0), windows.get(d)));
        }
        return rows;
    }
}
