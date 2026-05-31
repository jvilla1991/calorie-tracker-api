package com.calorietracker.goals;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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

    private DailyGoal defaultGoal() {
        DailyGoal g = new DailyGoal();
        g.setCalories(2000);
        g.setProtein(java.math.BigDecimal.valueOf(150));
        g.setCarbs(java.math.BigDecimal.valueOf(200));
        g.setFat(java.math.BigDecimal.valueOf(65));
        g.setFiber(java.math.BigDecimal.valueOf(30));
        return g;
    }
}
