package com.calorietracker.goals;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class GoalsService {

    private final DailyGoalRepository goalRepo;

    public GoalsService(DailyGoalRepository goalRepo) {
        this.goalRepo = goalRepo;
    }

    @Transactional(readOnly = true)
    public DailyGoal getCurrent() {
        return goalRepo.findCurrentGoal(LocalDate.now())
                .orElse(defaultGoal());
    }

    @Transactional
    public DailyGoal update(GoalsRequest req) {
        DailyGoal goal = new DailyGoal();
        goal.setCalories(req.getCalories());
        goal.setProtein(req.getProtein());
        goal.setCarbs(req.getCarbs());
        goal.setFat(req.getFat());
        goal.setFiber(req.getFiber());
        goal.setEffectiveFrom(LocalDate.now());
        return goalRepo.save(goal);
    }

    /**
     * The calories goal in effect on each date in {@code [from, to]}.
     * A date uses the goal row with the latest {@code effective_from <= date}; when several rows
     * share that date, the most recently saved (highest id) wins. Dates earlier than every goal
     * use the earliest goal, and if no goals exist at all the built-in default applies.
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, Integer> caloriesGoalsByDate(LocalDate from, LocalDate to) {
        return resolveCaloriesGoals(goalRepo.findAllByOrderByEffectiveFromAscIdAsc(), from, to);
    }

    /** Pure resolution step of {@link #caloriesGoalsByDate}; {@code history} must be oldest first. */
    static Map<LocalDate, Integer> resolveCaloriesGoals(List<DailyGoal> history,
                                                        LocalDate from, LocalDate to) {
        TreeMap<LocalDate, Integer> timeline = new TreeMap<>();
        for (DailyGoal g : history) {
            timeline.put(g.getEffectiveFrom(), g.getCalories()); // later row on same date overwrites
        }
        Map<LocalDate, Integer> result = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Map.Entry<LocalDate, Integer> applicable = timeline.floorEntry(d);
            if (applicable == null) applicable = timeline.firstEntry();
            result.put(d, applicable != null ? applicable.getValue() : defaultGoal().getCalories());
        }
        return result;
    }

    private static DailyGoal defaultGoal() {
        DailyGoal g = new DailyGoal();
        g.setCalories(2000);
        g.setProtein(java.math.BigDecimal.valueOf(150));
        g.setCarbs(java.math.BigDecimal.valueOf(200));
        g.setFat(java.math.BigDecimal.valueOf(65));
        g.setFiber(java.math.BigDecimal.valueOf(30));
        return g;
    }
}
