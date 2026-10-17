package com.calorietracker.exercise;

import com.calorietracker.diary.DiaryEntryRepository;
import com.calorietracker.diary.DiaryService;
import com.calorietracker.diary.DiarySummaryService;
import com.calorietracker.diary.dto.DailySummaryDto;
import com.calorietracker.diary.dto.DiaryResponse;
import com.calorietracker.exercise.dto.ExerciseRequest;
import com.calorietracker.exercise.dto.ExerciseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercise log against real Postgres (V9 migration, repository queries, diary + summary wiring).
 * {@code @Transactional} rolls every inserted row back.
 */
@SpringBootTest
@Transactional
class ExerciseIT {

    // Far in the past so it cannot collide with real diary dates.
    private static final LocalDate D1 = LocalDate.of(1990, 7, 1);

    @Autowired ExerciseService exerciseService;
    @Autowired ExerciseEntryRepository repo;
    @Autowired DiaryService diaryService;
    @Autowired DiarySummaryService summaryService;
    @Autowired DiaryEntryRepository entryRepo;

    private ExerciseResponse add(LocalDate date, String name, int calories) {
        return exerciseService.add(date, new ExerciseRequest(name, BigDecimal.valueOf(calories)));
    }

    @Test
    void createListDelete() {
        ExerciseResponse run = add(D1, " Morning run ", 280);
        ExerciseResponse walk = add(D1, "Walk", 120);
        add(D1.plusDays(1), "Other day", 50);
        repo.flush();

        assertThat(run.id()).isNotNull();
        assertThat(run.name()).isEqualTo("Morning run");
        assertThat(exerciseService.forDay(D1)).containsExactly(run, walk);

        exerciseService.delete(run.id());
        repo.flush();
        assertThat(exerciseService.forDay(D1)).containsExactly(walk);

        assertThatThrownBy(() -> exerciseService.delete(run.id()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void dbRejectsNonPositiveCalories() {
        ExerciseEntry bad = new ExerciseEntry();
        bad.setEntryDate(D1);
        bad.setName("x");
        bad.setCalories(0);
        assertThatThrownBy(() -> repo.saveAndFlush(bad)).isInstanceOf(Exception.class);
    }

    @Test
    void diaryDay_withExerciseButNoFood_returnsExercise_andCreatesNoDiaryRow() {
        ExerciseResponse walk = add(D1, "Walk", 120);
        ExerciseResponse run = add(D1, "Run", 300);
        repo.flush();

        DiaryResponse day = diaryService.getDay(D1);

        assertThat(day.date()).isEqualTo(D1);
        assertThat(day.meals()).isEmpty();
        assertThat(day.exercise()).containsExactly(walk, run); // id ascending
        assertThat(day.exerciseCalories()).isEqualTo(420);
        assertThat(entryRepo.findByEntryDate(D1)).isEmpty();
    }

    @Test
    void diaryDay_withoutExercise_hasEmptyListAndZero() {
        DiaryResponse day = diaryService.getDay(D1.plusDays(20));
        assertThat(day.exercise()).isEmpty();
        assertThat(day.exerciseCalories()).isZero();
    }

    @Test
    void summary_hasPerDayExerciseAcrossRangeWithGaps() {
        add(D1, "Run", 300);
        add(D1, "Walk", 100);
        add(D1.plusDays(3), "Swim", 250);
        add(D1.plusDays(10), "Outside range", 999);
        repo.flush();

        List<DailySummaryDto> rows = summaryService.summarize(D1, D1.plusDays(4));

        assertThat(rows).extracting(DailySummaryDto::date)
                .containsExactly(D1, D1.plusDays(1), D1.plusDays(2), D1.plusDays(3), D1.plusDays(4));
        assertThat(rows).extracting(DailySummaryDto::exerciseCalories)
                .containsExactly(400, 0, 0, 250, 0);
        // Exercise does not leak into the food calories.
        assertThat(rows.get(0).calories()).isEqualByComparingTo("0");
    }
}
