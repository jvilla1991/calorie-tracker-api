package com.calorietracker.diary;

import com.calorietracker.food.FoodService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the page/size clamping in {@link DiaryService#getRecentFoods(int, int)}. */
@ExtendWith(MockitoExtension.class)
class RecentFoodsPagingTest {

    @Mock DiaryEntryRepository entryRepo;
    @Mock DiaryMealRepository mealRepo;
    @Mock DiaryItemRepository itemRepo;
    @Mock FoodService foodService;

    @InjectMocks DiaryService diaryService;

    @ParameterizedTest(name = "page={0}, size={1} -> page {2}, size {3}")
    @CsvSource({
            "0,   20,  0, 20",   // defaults pass through
            "3,   10,  3, 10",   // normal paging
            "-2,  20,  0, 20",   // negative page clamps to 0
            "0,   500, 0, 50",   // size capped at 50
            "0,   50,  0, 50",   // cap is inclusive
            "0,   0,   0, 1",    // zero size clamps up to 1
            "1,   -5,  1, 1",    // negative size clamps up to 1
    })
    void clampsPageAndSize(int page, int size, int expectedPage, int expectedSize) {
        when(itemRepo.findRecentDistinctFoods(any())).thenReturn(List.of());

        diaryService.getRecentFoods(page, size);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(itemRepo).findRecentDistinctFoods(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(expectedPage);
        assertThat(captor.getValue().getPageSize()).isEqualTo(expectedSize);
    }

    @Test
    void defaultSizeConstantsMatchTheApiContract() {
        assertThat(DiaryService.RECENT_DEFAULT_SIZE).isEqualTo(20);
        assertThat(DiaryService.RECENT_MAX_SIZE).isEqualTo(50);
    }
}
