package com.calorietracker.food;

import com.calorietracker.food.OpenFoodFactsClient.OFFProduct;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FoodService#search} — the categorized food search.
 * Pure Mockito (no DB / Spring context): verifies the "mine" vs "external"
 * split and that repeated external hits are deduped, not re-inserted.
 */
@ExtendWith(MockitoExtension.class)
class FoodServiceTest {

    @Mock FoodRepository foodRepository;
    @Mock OpenFoodFactsClient offClient;

    @InjectMocks FoodService foodService;

    private static Food food(String name, String barcode, String source) {
        Food f = new Food();
        f.setName(name);
        f.setBarcode(barcode);
        f.setSource(source);
        return f;
    }

    private static OFFProduct offProduct(String code, String name) {
        OFFProduct p = new OFFProduct();
        p.code = code;
        p.productName = name;
        return p;
    }

    @Test
    void search_returnsMyFoodsAndExternalSeparately() {
        when(foodRepository.searchByNameAndSource("chicken", "custom"))
                .thenReturn(List.of(food("My Chicken", null, "custom")));
        when(foodRepository.searchByNameAndSource("chicken", "openfoodfacts"))
                .thenReturn(List.of());
        when(offClient.search("chicken"))
                .thenReturn(List.of(offProduct("123", "Brand Chicken")));
        when(foodRepository.findByBarcode("123")).thenReturn(Optional.empty());
        when(foodRepository.save(any(Food.class))).thenAnswer(inv -> inv.getArgument(0));

        FoodSearchResponse result = foodService.search("chicken");

        assertThat(result.mine()).extracting(FoodDto::name).containsExactly("My Chicken");
        assertThat(result.external()).extracting(FoodDto::name).containsExactly("Brand Chicken");
    }

    @Test
    void search_dedupesExternalByBarcodeAndDoesNotReinsert() {
        Food cached = food("Cached Chicken", "123", "openfoodfacts");
        when(foodRepository.searchByNameAndSource("chicken", "custom")).thenReturn(List.of());
        when(foodRepository.searchByNameAndSource("chicken", "openfoodfacts"))
                .thenReturn(List.of(cached));
        when(offClient.search("chicken"))
                .thenReturn(List.of(offProduct("123", "Brand Chicken")));
        when(foodRepository.findByBarcode("123")).thenReturn(Optional.of(cached));

        FoodSearchResponse result = foodService.search("chicken");

        // Same barcode from cache + API collapses to one row, using the cached name.
        assertThat(result.external()).extracting(FoodDto::name).containsExactly("Cached Chicken");
        // The already-cached product must not be inserted again.
        verify(foodRepository, never()).save(any(Food.class));
    }
}
