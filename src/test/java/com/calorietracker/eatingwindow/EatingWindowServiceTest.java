package com.calorietracker.eatingwindow;

import com.calorietracker.eatingwindow.dto.EatingWindowRequest;
import com.calorietracker.eatingwindow.dto.EatingWindowResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Validation and upsert rules of {@link EatingWindowService} (fixed clock, mocked repository). */
class EatingWindowServiceTest {

    /** "Now" for every test: 2026-10-16 16:00 at -04:00. */
    private static final Instant NOW = Instant.parse("2026-10-16T20:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 16);

    private EatingWindowRepository repo;
    private EatingWindowService service;

    @BeforeEach
    void setUp() {
        repo = mock(EatingWindowRepository.class);
        when(repo.findByEntryDate(any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new EatingWindowService(repo, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OffsetDateTime t(String iso) {
        return OffsetDateTime.parse(iso);
    }

    private EatingWindowResponse upsert(LocalDate date, String start, String end) {
        return service.upsert(date, new EatingWindowRequest(
                start == null ? null : t(start), end == null ? null : t(end)));
    }

    private void assert400(LocalDate date, String start, String end) {
        clearInvocations(repo); // a test may have saved a valid window first
        assertThatThrownBy(() -> upsert(date, start, end))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(repo, never()).save(any());
    }

    @Test
    void startOnly_isStoredAsUtcInstant() {
        EatingWindowResponse r = upsert(DAY, "2026-10-16T12:20:00-04:00", null);
        assertThat(r.startedAt()).isEqualTo(Instant.parse("2026-10-16T16:20:00Z"));
        assertThat(r.endedAt()).isNull();
    }

    @Test
    void startAndEnd_areStored() {
        EatingWindowResponse r = upsert(DAY, "2026-10-16T08:00:00-04:00", "2026-10-16T15:30:00-04:00");
        assertThat(r.startedAt()).isEqualTo(Instant.parse("2026-10-16T12:00:00Z"));
        assertThat(r.endedAt()).isEqualTo(Instant.parse("2026-10-16T19:30:00Z"));
    }

    @Test
    void replacingExistingRow_overwritesBothFields() {
        EatingWindow existing = new EatingWindow();
        existing.setEntryDate(DAY);
        existing.setStartedAt(Instant.parse("2026-10-16T10:00:00Z"));
        existing.setEndedAt(Instant.parse("2026-10-16T18:00:00Z"));
        when(repo.findByEntryDate(DAY)).thenReturn(Optional.of(existing));

        EatingWindowResponse r = upsert(DAY, "2026-10-16T13:00:00Z", null);

        verify(repo).save(existing);
        assertThat(r.startedAt()).isEqualTo(Instant.parse("2026-10-16T13:00:00Z"));
        assertThat(r.endedAt()).isNull(); // PUT replaces: omitted endedAt clears it
    }

    @Test
    void bothNull_is400_useDeleteInstead() {
        assert400(DAY, null, null);
    }

    @Test
    void endWithoutStart_is400() {
        assert400(DAY, null, "2026-10-16T15:00:00-04:00");
    }

    @Test
    void endBeforeStart_is400() {
        assert400(DAY, "2026-10-16T12:00:00-04:00", "2026-10-16T11:59:00-04:00");
    }

    @Test
    void endEqualToStart_isAllowed() {
        upsert(DAY, "2026-10-16T12:00:00-04:00", "2026-10-16T12:00:00-04:00");
    }

    @Test
    void exactly24h_isAllowed_longerIs400() {
        LocalDate day = LocalDate.of(2026, 10, 14);
        upsert(day, "2026-10-14T08:00:00-04:00", "2026-10-15T08:00:00-04:00");
        assert400(day, "2026-10-14T08:00:00-04:00", "2026-10-15T08:01:00-04:00");
    }

    @Test
    void startMoreThanADayBeforeTheDate_is400_exactlyOneDayIsAllowed() {
        // Start of 2026-10-16 at -04:00 is 2026-10-16T00:00-04:00; one day earlier is the limit.
        upsert(DAY, "2026-10-15T00:00:00-04:00", null);
        assert400(DAY, "2026-10-14T23:59:00-04:00", null);
    }

    @Test
    void startMoreThanADayAfterTheDate_is400() {
        LocalDate day = LocalDate.of(2026, 10, 10);
        // End of 2026-10-10 is 2026-10-11T00:00; one day later is 2026-10-12T00:00.
        upsert(day, "2026-10-12T00:00:00-04:00", null);
        assert400(day, "2026-10-12T00:01:00-04:00", null);
    }

    @Test
    void startInFuture_beyondSkew_is400_withinSkewIsAllowed() {
        upsert(DAY, "2026-10-16T16:04:00-04:00", null);           // now + 4 min
        assert400(DAY, "2026-10-16T16:06:00-04:00", null);        // now + 6 min
    }

    @Test
    void endInFuture_beyondSkew_is400() {
        assert400(DAY, "2026-10-16T12:00:00-04:00", "2026-10-16T16:06:00-04:00");
    }

    @Test
    void delete_isIdempotent() {
        service.delete(DAY); // nothing stored: no error
        verify(repo, never()).delete(any());

        EatingWindow existing = new EatingWindow();
        when(repo.findByEntryDate(DAY)).thenReturn(Optional.of(existing));
        service.delete(DAY);
        verify(repo).delete(existing);
    }
}
