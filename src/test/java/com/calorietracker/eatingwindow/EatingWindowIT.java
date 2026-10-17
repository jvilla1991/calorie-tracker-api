package com.calorietracker.eatingwindow;

import com.calorietracker.diary.DiaryEntryRepository;
import com.calorietracker.diary.DiaryService;
import com.calorietracker.diary.DiarySummaryService;
import com.calorietracker.diary.dto.DailySummaryDto;
import com.calorietracker.diary.dto.DiaryResponse;
import com.calorietracker.eatingwindow.dto.EatingWindowRequest;
import com.calorietracker.eatingwindow.dto.EatingWindowResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Eating windows against real Postgres (V10 migration, upsert by date, diary + summary wiring)
 * and the application's real Jackson configuration for the JSON date format.
 * {@code @Transactional} rolls every inserted row back.
 */
@SpringBootTest
@Transactional
class EatingWindowIT {

    // Far in the past so it cannot collide with real diary dates.
    private static final LocalDate D1 = LocalDate.of(1990, 8, 1);

    @Autowired EatingWindowService service;
    @Autowired EatingWindowRepository repo;
    @Autowired DiaryService diaryService;
    @Autowired DiarySummaryService summaryService;
    @Autowired DiaryEntryRepository entryRepo;
    @Autowired WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private EatingWindowResponse save(LocalDate date, String start, String end) {
        return service.upsert(date, new EatingWindowRequest(
                start == null ? null : OffsetDateTime.parse(start),
                end == null ? null : OffsetDateTime.parse(end)));
    }

    @Test
    void upsert_createThenReplace_keepsOneRow() {
        save(D1, "1990-08-01T08:00:00-04:00", "1990-08-01T16:00:00-04:00");
        save(D1, "1990-08-01T10:00:00-04:00", "1990-08-01T18:00:00-04:00");
        repo.flush();

        assertThat(repo.findAll().stream().filter(w -> w.getEntryDate().equals(D1))).hasSize(1);
        assertThat(service.forDay(D1)).contains(new EatingWindowResponse(
                Instant.parse("1990-08-01T14:00:00Z"), Instant.parse("1990-08-01T22:00:00Z")));
    }

    @Test
    void partial_startOnlyThenEnd() {
        save(D1, "1990-08-01T12:20:00-04:00", null);
        repo.flush();
        assertThat(service.forDay(D1)).contains(
                new EatingWindowResponse(Instant.parse("1990-08-01T16:20:00Z"), null));

        save(D1, "1990-08-01T12:20:00-04:00", "1990-08-01T20:05:00-04:00");
        repo.flush();
        assertThat(service.forDay(D1)).contains(new EatingWindowResponse(
                Instant.parse("1990-08-01T16:20:00Z"), Instant.parse("1990-08-02T00:05:00Z")));
    }

    @Test
    void delete_removesRow_andIsIdempotent() {
        save(D1, "1990-08-01T12:00:00Z", null);
        repo.flush();
        service.delete(D1);
        repo.flush();
        assertThat(service.forDay(D1)).isEmpty();
        service.delete(D1); // again: no error
    }

    @Test
    void diaryDay_includesEatingWindow_withoutCreatingDiaryRow() {
        save(D1, "1990-08-01T12:00:00Z", null);
        repo.flush();

        DiaryResponse day = diaryService.getDay(D1);
        assertThat(day.eatingWindow()).isEqualTo(
                new EatingWindowResponse(Instant.parse("1990-08-01T12:00:00Z"), null));
        assertThat(entryRepo.findByEntryDate(D1)).isEmpty();
        assertThat(diaryService.getDay(D1.plusDays(1)).eatingWindow()).isNull();
    }

    @Test
    void summary_hasPerDayEatingTimes_nullOnDaysWithout() {
        save(D1, "1990-08-01T12:00:00Z", "1990-08-01T20:00:00Z");
        save(D1.plusDays(2), "1990-08-03T13:00:00Z", null);
        repo.flush();

        List<DailySummaryDto> rows = summaryService.summarize(D1, D1.plusDays(3));

        assertThat(rows).extracting(DailySummaryDto::eatingStartedAt).containsExactly(
                Instant.parse("1990-08-01T12:00:00Z"), null, Instant.parse("1990-08-03T13:00:00Z"), null);
        assertThat(rows).extracting(DailySummaryDto::eatingEndedAt).containsExactly(
                Instant.parse("1990-08-01T20:00:00Z"), null, null, null);
    }

    // --- JSON over the real MVC stack (application Jackson config) ------------------------

    @Test
    void json_putResponseUsesIsoUtcStrings() throws Exception {
        mvc.perform(put("/api/diary/1990-08-01/eating-window").with(user("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedAt\":\"1990-08-01T12:20:00-04:00\",\"endedAt\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startedAt").value("1990-08-01T16:20:00Z"))
                .andExpect(jsonPath("$.endedAt").value(nullValue()));
    }

    @Test
    void json_diaryAndSummaryUseIsoUtcStrings_andNullWhenAbsent() throws Exception {
        save(D1, "1990-08-01T12:20:00-04:00", "1990-08-01T20:00:00-04:00");
        repo.flush();

        mvc.perform(get("/api/diary/1990-08-01").with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eatingWindow.startedAt").value("1990-08-01T16:20:00Z"))
                .andExpect(jsonPath("$.eatingWindow.endedAt").value("1990-08-02T00:00:00Z"));

        mvc.perform(get("/api/diary/1990-08-02").with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eatingWindow").value(nullValue()));

        mvc.perform(get("/api/diary/summary").param("from", "1990-08-01").param("to", "1990-08-02")
                        .with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eatingStartedAt").value("1990-08-01T16:20:00Z"))
                .andExpect(jsonPath("$[0].eatingEndedAt").value("1990-08-02T00:00:00Z"))
                .andExpect(jsonPath("$[1].eatingStartedAt").value(nullValue()))
                .andExpect(jsonPath("$[1].eatingEndedAt").value(nullValue()));
    }

    @Test
    void json_deleteIs204() throws Exception {
        mvc.perform(delete("/api/diary/1990-08-01/eating-window").with(user("admin")))
                .andExpect(status().isNoContent());
    }
}
