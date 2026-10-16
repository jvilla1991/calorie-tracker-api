package com.calorietracker.weighin;

import com.calorietracker.weighin.dto.WeighInRequest;
import com.calorietracker.weighin.dto.WeighInResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Weigh-ins against real Postgres (V8 migration + repository queries) and the security rule.
 * {@code @Transactional} rolls every inserted row back.
 */
@SpringBootTest
@Transactional
class WeighInIT {

    // Far in the past so it cannot collide with real weigh-ins.
    private static final LocalDate D1 = LocalDate.of(1990, 5, 1);

    @Autowired WeighInService service;
    @Autowired WeighInRepository repo;
    @Autowired WebApplicationContext context;

    @Test
    void upsertSameDateTwice_keepsOneRowWithLatestWeight() {
        WeighInService.UpsertResult first = service.upsert(new WeighInRequest(D1, new BigDecimal("82.4")));
        WeighInService.UpsertResult second = service.upsert(new WeighInRequest(D1, new BigDecimal("81.65")));
        repo.flush();

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.weighIn().id()).isEqualTo(first.weighIn().id());
        assertThat(service.list(D1, D1)).containsExactly(
                new WeighInResponse(first.weighIn().id(), D1, new BigDecimal("81.650")));
    }

    @Test
    void list_isAscendingAndRespectsInclusiveBounds() {
        service.upsert(new WeighInRequest(D1.plusDays(2), new BigDecimal("80")));
        service.upsert(new WeighInRequest(D1, new BigDecimal("82")));
        service.upsert(new WeighInRequest(D1.plusDays(1), new BigDecimal("81")));
        repo.flush();

        assertThat(service.list(D1, D1.plusDays(2))).extracting(WeighInResponse::date)
                .containsExactly(D1, D1.plusDays(1), D1.plusDays(2));
        assertThat(service.list(D1.plusDays(1), D1.plusDays(2))).hasSize(2);
        assertThat(service.list(null, D1)).extracting(WeighInResponse::date).contains(D1)
                .doesNotContain(D1.plusDays(1));
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/weigh-ins")).andExpect(status().isUnauthorized());
    }
}
