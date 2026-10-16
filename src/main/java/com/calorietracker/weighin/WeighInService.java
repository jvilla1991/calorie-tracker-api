package com.calorietracker.weighin;

import com.calorietracker.weighin.dto.WeighInRequest;
import com.calorietracker.weighin.dto.WeighInResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class WeighInService {

    /** Result of an upsert: the saved row, and whether it was newly created (vs. replaced). */
    public record UpsertResult(WeighInResponse weighIn, boolean created) {}

    private final WeighInRepository repo;

    public WeighInService(WeighInRepository repo) {
        this.repo = repo;
    }

    /**
     * Weigh-ins ascending by date. Either bound may be null (open-ended); both null returns all.
     *
     * @throws ResponseStatusException 400 if both bounds are given and {@code from > to}
     */
    @Transactional(readOnly = true)
    public List<WeighInResponse> list(LocalDate from, LocalDate to) {
        List<WeighIn> rows;
        if (from != null && to != null) {
            if (from.isAfter(to)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
            }
            rows = repo.findByWeighDateBetweenOrderByWeighDateAsc(from, to);
        } else if (from != null) {
            rows = repo.findByWeighDateGreaterThanEqualOrderByWeighDateAsc(from);
        } else if (to != null) {
            rows = repo.findByWeighDateLessThanEqualOrderByWeighDateAsc(to);
        } else {
            rows = repo.findAllByOrderByWeighDateAsc();
        }
        return rows.stream().map(WeighInResponse::from).toList();
    }

    /**
     * Logs a weight for a date, replacing any existing weigh-in on that date.
     * The weight is stored rounded to 3 decimal places (the column's scale).
     */
    @Transactional
    public UpsertResult upsert(WeighInRequest req) {
        WeighIn w = repo.findByWeighDate(req.date()).orElse(null);
        boolean created = w == null;
        if (created) {
            w = new WeighIn();
            w.setWeighDate(req.date());
        }
        w.setWeightKg(req.weightKg().setScale(3, RoundingMode.HALF_UP));
        return new UpsertResult(WeighInResponse.from(repo.save(w)), created);
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Weigh-in not found");
        }
        repo.deleteById(id);
    }
}
