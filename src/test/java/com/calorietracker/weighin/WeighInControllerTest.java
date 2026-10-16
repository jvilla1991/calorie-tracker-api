package com.calorietracker.weighin;

import com.calorietracker.weighin.dto.WeighInResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of /api/weigh-ins (standalone MockMvc with a mocked service, bean validation on). */
class WeighInControllerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 16);

    private WeighInService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(WeighInService.class);
        mvc = MockMvcBuilders.standaloneSetup(new WeighInController(service)).build();
    }

    private static String body(String date, String weightKg) {
        StringBuilder sb = new StringBuilder("{");
        if (date != null) sb.append("\"date\":\"").append(date).append("\"");
        if (date != null && weightKg != null) sb.append(",");
        if (weightKg != null) sb.append("\"weightKg\":").append(weightKg);
        return sb.append("}").toString();
    }

    @Test
    void get_returnsAscendingArrayWithDocumentedShape() throws Exception {
        when(service.list(null, null)).thenReturn(List.of(
                new WeighInResponse(1L, DAY.minusDays(1), new BigDecimal("82.100")),
                new WeighInResponse(2L, DAY, new BigDecimal("81.650"))));

        mvc.perform(get("/api/weigh-ins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].date").value("2026-10-16"))
                .andExpect(jsonPath("$[1].weightKg").value(81.65));
    }

    @Test
    void get_passesOptionalRange() throws Exception {
        mvc.perform(get("/api/weigh-ins").param("from", "2026-10-01").param("to", "2026-10-16"))
                .andExpect(status().isOk());
        verify(service).list(LocalDate.of(2026, 10, 1), DAY);
    }

    @Test
    void post_newDate_is201() throws Exception {
        when(service.upsert(any())).thenReturn(new WeighInService.UpsertResult(
                new WeighInResponse(1L, DAY, new BigDecimal("81.650")), true));

        mvc.perform(post("/api/weigh-ins").contentType(MediaType.APPLICATION_JSON).content(body("2026-10-16", "81.65")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.date").value("2026-10-16"))
                .andExpect(jsonPath("$.weightKg").value(81.65));
    }

    @Test
    void post_existingDate_is200() throws Exception {
        when(service.upsert(any())).thenReturn(new WeighInService.UpsertResult(
                new WeighInResponse(1L, DAY, new BigDecimal("80.000")), false));

        mvc.perform(post("/api/weigh-ins").contentType(MediaType.APPLICATION_JSON).content(body("2026-10-16", "80")))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "700", "700.5", "1000"})
    void post_weightOutOfRange_is400(String weight) throws Exception {
        mvc.perform(post("/api/weigh-ins").contentType(MediaType.APPLICATION_JSON).content(body("2026-10-01", weight)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void post_missingFields_are400() throws Exception {
        mvc.perform(post("/api/weigh-ins").contentType(MediaType.APPLICATION_JSON).content(body(null, "80")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/weigh-ins").contentType(MediaType.APPLICATION_JSON).content(body("2026-10-01", null)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void post_futureDate_is400() throws Exception {
        String tomorrow = LocalDate.now().plusDays(1).toString();
        mvc.perform(post("/api/weigh-ins").contentType(MediaType.APPLICATION_JSON).content(body(tomorrow, "80")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void post_today_isAllowed() throws Exception {
        when(service.upsert(any())).thenReturn(new WeighInService.UpsertResult(
                new WeighInResponse(1L, LocalDate.now(), new BigDecimal("80.000")), true));
        mvc.perform(post("/api/weigh-ins").contentType(MediaType.APPLICATION_JSON)
                        .content(body(LocalDate.now().toString(), "80")))
                .andExpect(status().isCreated());
    }

    @Test
    void delete_is204() throws Exception {
        mvc.perform(delete("/api/weigh-ins/5")).andExpect(status().isNoContent());
        verify(service).delete(5L);
    }

    @Test
    void delete_missing_is404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(service).delete(5L);
        mvc.perform(delete("/api/weigh-ins/5")).andExpect(status().isNotFound());
    }
}
