package com.calorietracker.eatingwindow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP status contract of the eating-window endpoints (standalone MockMvc, real service with a
 * fixed clock over a mocked repository). JSON date format is asserted in {@code EatingWindowIT}
 * against the application's real Jackson configuration.
 */
class EatingWindowControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-16T20:00:00Z");

    private EatingWindowRepository repo;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repo = mock(EatingWindowRepository.class);
        when(repo.findByEntryDate(any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        mvc = MockMvcBuilders.standaloneSetup(new EatingWindowController(
                new EatingWindowService(repo, Clock.fixed(NOW, ZoneOffset.UTC)))).build();
    }

    private int putStatus(String date, String json) throws Exception {
        return mvc.perform(put("/api/diary/" + date + "/eating-window")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void put_valid_is200() throws Exception {
        mvc.perform(put("/api/diary/2026-10-16/eating-window").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedAt\":\"2026-10-16T12:20:00-04:00\",\"endedAt\":null}"))
                .andExpect(status().isOk());
    }

    @Test
    void put_invalidCombinations_are400() throws Exception {
        assertThat(putStatus("2026-10-16",
                "{\"startedAt\":null,\"endedAt\":null}")).isEqualTo(400);
        assertThat(putStatus("2026-10-16", "{}")).isEqualTo(400);
        assertThat(putStatus("2026-10-16",
                "{\"endedAt\":\"2026-10-16T15:00:00-04:00\"}")).isEqualTo(400);
        assertThat(putStatus("2026-10-16",
                "{\"startedAt\":\"2026-10-16T12:00:00-04:00\",\"endedAt\":\"2026-10-16T11:00:00-04:00\"}"))
                .isEqualTo(400);
        verify(repo, never()).save(any());
    }

    @Test
    void put_timeWithoutOffset_is400() throws Exception {
        assertThat(putStatus("2026-10-16",
                "{\"startedAt\":\"2026-10-16T12:20:00\"}")).isEqualTo(400);
        assertThat(putStatus("2026-10-16",
                "{\"startedAt\":\"not-a-time\"}")).isEqualTo(400);
        verify(repo, never()).save(any());
    }

    @Test
    void put_malformedDate_is400() throws Exception {
        assertThat(putStatus("16-10-2026",
                "{\"startedAt\":\"2026-10-16T12:20:00-04:00\"}")).isEqualTo(400);
    }

    @Test
    void delete_is204_evenWhenNothingStored() throws Exception {
        mvc.perform(delete("/api/diary/2026-10-16/eating-window")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/diary/2026-10-16/eating-window")).andExpect(status().isNoContent());
    }
}
