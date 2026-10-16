package com.calorietracker.weighin;

import com.calorietracker.weighin.dto.WeighInRequest;
import com.calorietracker.weighin.dto.WeighInResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for {@link WeighInService} (Mockito, no DB). */
@ExtendWith(MockitoExtension.class)
class WeighInServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 16);

    @Mock WeighInRepository repo;

    @InjectMocks WeighInService service;

    private static WeighIn stored(long id, LocalDate date, String kg) {
        WeighIn w = new WeighIn();
        ReflectionTestUtils.setField(w, "id", id);
        w.setWeighDate(date);
        w.setWeightKg(new BigDecimal(kg));
        return w;
    }

    @Test
    void upsert_newDate_createsRow_roundedToThreeDecimals() {
        when(repo.findByWeighDate(DAY)).thenReturn(Optional.empty());
        when(repo.save(any(WeighIn.class))).thenAnswer(inv -> {
            WeighIn w = inv.getArgument(0);
            ReflectionTestUtils.setField(w, "id", 1L);
            return w;
        });

        WeighInService.UpsertResult result = service.upsert(new WeighInRequest(DAY, new BigDecimal("81.65")));

        assertThat(result.created()).isTrue();
        assertThat(result.weighIn()).isEqualTo(new WeighInResponse(1L, DAY, new BigDecimal("81.650")));
    }

    @Test
    void upsert_existingDate_replacesWeightOnSameRow() {
        WeighIn existing = stored(7L, DAY, "82.000");
        when(repo.findByWeighDate(DAY)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        WeighInService.UpsertResult result = service.upsert(new WeighInRequest(DAY, new BigDecimal("81.2345")));

        assertThat(result.created()).isFalse();
        assertThat(result.weighIn().id()).isEqualTo(7L);
        assertThat(result.weighIn().weightKg()).isEqualTo(new BigDecimal("81.235"));
        verify(repo).save(existing);
    }

    @Test
    void delete_missing_is404() {
        when(repo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(repo, never()).deleteById(any());
    }

    @Test
    void delete_existing_deletes() {
        when(repo.existsById(7L)).thenReturn(true);

        service.delete(7L);

        verify(repo).deleteById(7L);
    }

    @Test
    void list_picksQueryByWhichBoundsAreGiven() {
        LocalDate from = DAY.minusDays(7);
        when(repo.findAllByOrderByWeighDateAsc()).thenReturn(List.of(stored(1L, DAY, "80.000")));

        assertThat(service.list(null, null)).extracting(WeighInResponse::id).containsExactly(1L);
        service.list(from, DAY);
        service.list(from, null);
        service.list(null, DAY);

        verify(repo).findByWeighDateBetweenOrderByWeighDateAsc(from, DAY);
        verify(repo).findByWeighDateGreaterThanEqualOrderByWeighDateAsc(from);
        verify(repo).findByWeighDateLessThanEqualOrderByWeighDateAsc(DAY);
    }

    @Test
    void list_fromAfterTo_is400() {
        assertThatThrownBy(() -> service.list(DAY, DAY.minusDays(1)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repo);
    }
}
