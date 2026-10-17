package com.calorietracker.exercise;

import com.calorietracker.exercise.dto.ExerciseRequest;
import com.calorietracker.exercise.dto.ExerciseResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExerciseService {

    public static final int NAME_MAX_LENGTH = 60;

    private final ExerciseEntryRepository repo;

    public ExerciseService(ExerciseEntryRepository repo) {
        this.repo = repo;
    }

    /**
     * Logs an exercise on {@code date}. The name is trimmed before it is checked and stored.
     *
     * @throws ResponseStatusException 400 if {@code date} is in the future (server clock) or the
     *                                 trimmed name is empty or longer than {@value #NAME_MAX_LENGTH}
     */
    @Transactional
    public ExerciseResponse add(LocalDate date, ExerciseRequest req) {
        if (date.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must not be in the future");
        }
        String name = req.name() == null ? "" : req.name().trim();
        if (name.isEmpty() || name.length() > NAME_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Name must be 1-" + NAME_MAX_LENGTH + " characters");
        }
        ExerciseEntry e = new ExerciseEntry();
        e.setEntryDate(date);
        e.setName(name);
        e.setCalories(req.calories().intValueExact());
        return ExerciseResponse.from(repo.save(e));
    }

    /** @throws ResponseStatusException 404 if no such entry */
    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise entry not found");
        }
        repo.deleteById(id);
    }

    /** The day's entries, oldest (lowest id) first; empty when none. */
    @Transactional(readOnly = true)
    public List<ExerciseResponse> forDay(LocalDate date) {
        return repo.findByEntryDateOrderByIdAsc(date).stream().map(ExerciseResponse::from).toList();
    }

    /** Calories burned per day in {@code [from, to]}; days without exercise are absent. One query. */
    @Transactional(readOnly = true)
    public Map<LocalDate, Integer> caloriesByDate(LocalDate from, LocalDate to) {
        Map<LocalDate, Integer> result = new HashMap<>();
        for (DailyExerciseTotal t : repo.sumCaloriesByDate(from, to)) {
            result.put(t.date(), t.calories().intValue());
        }
        return result;
    }
}
