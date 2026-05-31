package com.calorietracker.goals;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/goals")
public class GoalsController {

    private final GoalsService goalsService;

    public GoalsController(GoalsService goalsService) {
        this.goalsService = goalsService;
    }

    @GetMapping
    public DailyGoal getGoals() {
        return goalsService.getCurrent();
    }

    @PutMapping
    public DailyGoal updateGoals(@Valid @RequestBody GoalsRequest req) {
        return goalsService.update(req);
    }
}
