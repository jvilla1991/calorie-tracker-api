package com.calorietracker.eatingwindow;

import com.calorietracker.eatingwindow.dto.EatingWindowRequest;
import com.calorietracker.eatingwindow.dto.EatingWindowResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Daily log of when eating started and stopped (intermittent fasting). A plain log: there are
 * no targets or schedules.
 */
@Service
public class EatingWindowService {

    /** Longest allowed window. */
    public static final Duration MAX_WINDOW = Duration.ofHours(24);
    /** How far into the future a time may be, to tolerate client/server clock skew. */
    public static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private final EatingWindowRepository repo;
    private final Clock clock;

    @Autowired
    public EatingWindowService(EatingWindowRepository repo) {
        this(repo, Clock.systemUTC());
    }

    EatingWindowService(EatingWindowRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    /**
     * Creates or replaces the window for {@code date}. Both fields are written as given, so
     * sending only {@code startedAt} clears a previously stored {@code endedAt}.
     * <p>
     * To clear a day, use {@link #delete}: a body with <b>both</b> fields null is rejected (400)
     * rather than treated as a delete, so an empty or mistaken PUT cannot silently wipe a day.
     *
     * @throws ResponseStatusException 400 if both fields are null; {@code endedAt} is given
     *     without {@code startedAt}; {@code endedAt} is before {@code startedAt}; the window is
     *     longer than 24h; {@code startedAt} is more than one day before the start of
     *     {@code date} or more than one day after its end (judged in {@code startedAt}'s own
     *     offset); or either time is more than 5 minutes in the future
     */
    @Transactional
    public EatingWindowResponse upsert(LocalDate date, EatingWindowRequest req) {
        OffsetDateTime start = req.startedAt();
        OffsetDateTime end = req.endedAt();
        if (start == null && end == null) {
            throw bad("Provide startedAt and/or endedAt; use DELETE to clear the day");
        }
        if (start == null) {
            throw bad("endedAt requires startedAt");
        }
        Instant latestAllowed = clock.instant().plus(CLOCK_SKEW);
        if (start.toInstant().isAfter(latestAllowed)) {
            throw bad("startedAt must not be in the future");
        }
        ZoneOffset offset = start.getOffset();
        Instant dayStart = date.atStartOfDay().toInstant(offset);
        Instant dayEnd = date.plusDays(1).atStartOfDay().toInstant(offset);
        if (start.toInstant().isBefore(dayStart.minus(Duration.ofDays(1)))
                || start.toInstant().isAfter(dayEnd.plus(Duration.ofDays(1)))) {
            throw bad("startedAt must be within one day of " + date);
        }
        if (end != null) {
            if (end.toInstant().isBefore(start.toInstant())) {
                throw bad("endedAt must not be before startedAt");
            }
            if (Duration.between(start.toInstant(), end.toInstant()).compareTo(MAX_WINDOW) > 0) {
                throw bad("Eating window must be at most 24 hours");
            }
            if (end.toInstant().isAfter(latestAllowed)) {
                throw bad("endedAt must not be in the future");
            }
        }

        EatingWindow w = repo.findByEntryDate(date).orElseGet(() -> {
            EatingWindow n = new EatingWindow();
            n.setEntryDate(date);
            return n;
        });
        w.setStartedAt(start.toInstant());
        w.setEndedAt(end == null ? null : end.toInstant());
        return EatingWindowResponse.from(repo.save(w));
    }

    /** Removes the window for {@code date}, if any. Idempotent. */
    @Transactional
    public void delete(LocalDate date) {
        repo.findByEntryDate(date).ifPresent(repo::delete);
    }

    @Transactional(readOnly = true)
    public Optional<EatingWindowResponse> forDay(LocalDate date) {
        return repo.findByEntryDate(date).map(EatingWindowResponse::from);
    }

    /** Windows for every day in {@code [from, to]} that has one. One query. */
    @Transactional(readOnly = true)
    public Map<LocalDate, EatingWindowResponse> byDate(LocalDate from, LocalDate to) {
        Map<LocalDate, EatingWindowResponse> result = new HashMap<>();
        for (EatingWindow w : repo.findByEntryDateBetween(from, to)) {
            result.put(w.getEntryDate(), EatingWindowResponse.from(w));
        }
        return result;
    }

    private static ResponseStatusException bad(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
