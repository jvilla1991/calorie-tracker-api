package com.calorietracker.exercise;

import com.calorietracker.diary.DiaryController;
import com.calorietracker.diary.DiaryService;
import com.calorietracker.diary.DiarySummaryService;
import com.calorietracker.diary.dto.DiaryResponse;
import com.calorietracker.exercise.dto.ExerciseResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of the exercise endpoints: standalone MockMvc with the real {@link ExerciseService}
 * over a mocked repository, so the service-side name rules are exercised over HTTP too.
 */
class ExerciseControllerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    private ExerciseEntryRepository repo;
    private DiaryService diaryService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repo = mock(ExerciseEntryRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        diaryService = mock(DiaryService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new ExerciseController(new ExerciseService(repo)),
                new DiaryController(diaryService, mock(DiarySummaryService.class))).build();
    }

    private static String body(String nameJson, String calories) {
        return "{\"name\":" + nameJson + ",\"calories\":" + calories + "}";
    }

    private void postExercise(String date, String json, int expectedStatus) throws Exception {
        mvc.perform(post("/api/diary/" + date + "/exercise")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void post_is201WithDocumentedShape_andTrimsName() throws Exception {
        mvc.perform(post("/api/diary/2026-10-01/exercise")
                        .contentType(MediaType.APPLICATION_JSON).content(body("\"  Morning run  \"", "280")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Morning run"))
                .andExpect(jsonPath("$.calories").value(280))
                .andExpect(jsonPath("$.id").hasJsonPath());

        ArgumentCaptor<ExerciseEntry> saved = ArgumentCaptor.forClass(ExerciseEntry.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Morning run");
        assertThat(saved.getValue().getEntryDate()).isEqualTo(DAY);
        assertThat(saved.getValue().getCalories()).isEqualTo(280);
    }

    @Test
    void post_boundaryValues_areAccepted() throws Exception {
        postExercise("2026-10-01", body("\"x\"", "1"), 201);
        postExercise("2026-10-01", body("\"" + "a".repeat(60) + "\"", "5000"), 201);
        postExercise(LocalDate.now().toString(), body("\"Walk\"", "100"), 201);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "5001", "280.5", "null"})
    void post_badCalories_is400(String calories) throws Exception {
        postExercise("2026-10-01", body("\"Run\"", calories), 400);
        verify(repo, never()).save(any());
    }

    @Test
    void post_missingCalories_is400() throws Exception {
        postExercise("2026-10-01", "{\"name\":\"Run\"}", 400);
        verify(repo, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "\"   \"", "null"})
    void post_blankName_is400(String nameJson) throws Exception {
        postExercise("2026-10-01", body(nameJson, "100"), 400);
        verify(repo, never()).save(any());
    }

    @Test
    void post_nameOver60AfterTrim_is400() throws Exception {
        postExercise("2026-10-01", body("\"" + "a".repeat(61) + "\"", "100"), 400);
        // 60 chars plus surrounding spaces is fine: the limit applies after trimming.
        postExercise("2026-10-01", body("\"  " + "a".repeat(60) + "  \"", "100"), 201);
    }

    @Test
    void post_futureDate_is400() throws Exception {
        postExercise(LocalDate.now().plusDays(1).toString(), body("\"Run\"", "100"), 400);
        verify(repo, never()).save(any());
    }

    @Test
    void post_malformedDate_is400() throws Exception {
        postExercise("10-01-2026", body("\"Run\"", "100"), 400);
    }

    @Test
    void delete_is204() throws Exception {
        when(repo.existsById(5L)).thenReturn(true);
        mvc.perform(delete("/api/diary/exercise/5")).andExpect(status().isNoContent());
        verify(repo).deleteById(5L);
    }

    @Test
    void delete_missing_is404() throws Exception {
        when(repo.existsById(5L)).thenReturn(false);
        mvc.perform(delete("/api/diary/exercise/5")).andExpect(status().isNotFound());
        verify(repo, never()).deleteById(any());
    }

    @Test
    void diaryDay_serializesExerciseFields() throws Exception {
        when(diaryService.getDay(DAY)).thenReturn(DiaryResponse.empty(DAY).withExercise(List.of(
                new ExerciseResponse(1L, "Morning run", 280),
                new ExerciseResponse(2L, "Walk", 120))));

        mvc.perform(get("/api/diary/2026-10-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-10-01"))
                .andExpect(jsonPath("$.meals.length()").value(0))
                .andExpect(jsonPath("$.totals.calories").value(0))
                .andExpect(jsonPath("$.exercise.length()").value(2))
                .andExpect(jsonPath("$.exercise[0].id").value(1))
                .andExpect(jsonPath("$.exercise[0].name").value("Morning run"))
                .andExpect(jsonPath("$.exercise[0].calories").value(280))
                .andExpect(jsonPath("$.exerciseCalories").value(400));
    }

    @Test
    void diaryDay_withoutExercise_hasEmptyArrayAndZero() throws Exception {
        when(diaryService.getDay(DAY)).thenReturn(DiaryResponse.empty(DAY).withExercise(List.of()));

        mvc.perform(get("/api/diary/2026-10-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercise").isArray())
                .andExpect(jsonPath("$.exercise.length()").value(0))
                .andExpect(jsonPath("$.exerciseCalories").value(0));
    }
}
