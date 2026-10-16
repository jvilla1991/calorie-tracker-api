package com.calorietracker.food;

import com.calorietracker.diary.DiaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Parameter handling for GET /api/foods/recent (standalone MockMvc, no Spring context). */
class FoodControllerRecentTest {

    private DiaryService diaryService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        diaryService = mock(DiaryService.class);
        when(diaryService.getRecentFoods(anyInt(), anyInt())).thenReturn(List.of());
        mvc = MockMvcBuilders
                .standaloneSetup(new FoodController(mock(FoodService.class), diaryService))
                .build();
    }

    @Test
    void noParams_usesPageZeroSizeTwenty_andReturnsPlainArray() throws Exception {
        mvc.perform(get("/api/foods/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        verify(diaryService).getRecentFoods(0, 20);
    }

    @Test
    void pageAndSize_arePassedThrough() throws Exception {
        mvc.perform(get("/api/foods/recent").param("page", "2").param("size", "15"))
                .andExpect(status().isOk());
        verify(diaryService).getRecentFoods(2, 15);
    }

    @Test
    void legacyLimit_isAnAliasForSize() throws Exception {
        mvc.perform(get("/api/foods/recent").param("limit", "5"))
                .andExpect(status().isOk());
        verify(diaryService).getRecentFoods(0, 5);
    }

    @Test
    void sizeWinsOverLimitWhenBothSent() throws Exception {
        mvc.perform(get("/api/foods/recent").param("size", "7").param("limit", "5"))
                .andExpect(status().isOk());
        verify(diaryService).getRecentFoods(0, 7);
    }
}
