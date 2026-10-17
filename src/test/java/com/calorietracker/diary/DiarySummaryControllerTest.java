package com.calorietracker.diary;

import com.calorietracker.eatingwindow.EatingWindowRepository;
import com.calorietracker.eatingwindow.EatingWindowService;
import com.calorietracker.exercise.ExerciseEntryRepository;
import com.calorietracker.exercise.ExerciseService;
import com.calorietracker.goals.DailyGoal;
import com.calorietracker.goals.DailyGoalRepository;
import com.calorietracker.goals.GoalsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of GET /api/diary/summary: JSON shape and 400s.
 * Standalone MockMvc with the real services over mocked repositories.
 */
class DiarySummaryControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        DiaryItemRepository itemRepo = mock(DiaryItemRepository.class);
        when(itemRepo.findAllInDateRange(any(), any())).thenReturn(List.of());
        DailyGoalRepository goalRepo = mock(DailyGoalRepository.class);
        DailyGoal g = new DailyGoal();
        g.setCalories(2000);
        g.setProtein(BigDecimal.ONE);
        g.setCarbs(BigDecimal.ONE);
        g.setFat(BigDecimal.ONE);
        g.setFiber(BigDecimal.ONE);
        g.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        when(goalRepo.findAllByOrderByEffectiveFromAscIdAsc()).thenReturn(List.of(g));

        DiarySummaryService summary = new DiarySummaryService(itemRepo, mock(DiaryMealRepository.class),
                new GoalsService(goalRepo),
                new ExerciseService(mock(ExerciseEntryRepository.class)),
                new EatingWindowService(mock(EatingWindowRepository.class)));
        mvc = MockMvcBuilders
                .standaloneSetup(new DiaryController(mock(DiaryService.class), summary))
                .build();
    }

    @Test
    void returnsOneRowPerDayWithTheDocumentedShape() throws Exception {
        mvc.perform(get("/api/diary/summary").param("from", "2026-10-01").param("to", "2026-10-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].date").value("2026-10-01"))
                .andExpect(jsonPath("$[2].date").value("2026-10-03"))
                .andExpect(jsonPath("$[0].calories").value(0))
                .andExpect(jsonPath("$[0].protein").value(0))
                .andExpect(jsonPath("$[0].carbs").value(0))
                .andExpect(jsonPath("$[0].fat").value(0))
                .andExpect(jsonPath("$[0].fiber").value(0))
                .andExpect(jsonPath("$[0].goalCalories").value(2000))
                .andExpect(jsonPath("$[0].exerciseCalories").value(0))
                .andExpect(jsonPath("$[0].eatingStartedAt").value(nullValue()))
                .andExpect(jsonPath("$[0].eatingEndedAt").value(nullValue()));
    }

    @Test
    void missingParams_are400() throws Exception {
        mvc.perform(get("/api/diary/summary").param("from", "2026-10-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/diary/summary").param("to", "2026-10-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/diary/summary"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedDate_is400() throws Exception {
        mvc.perform(get("/api/diary/summary").param("from", "10/01/2026").param("to", "2026-10-03"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fromAfterTo_is400() throws Exception {
        mvc.perform(get("/api/diary/summary").param("from", "2026-10-05").param("to", "2026-10-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rangeOver366Days_is400() throws Exception {
        mvc.perform(get("/api/diary/summary").param("from", "2025-01-01").param("to", "2026-01-02"))
                .andExpect(status().isBadRequest());
    }
}
