package com.calorietracker.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FlywayConfigTest {

    /** With a transactional lock, V7's CREATE INDEX CONCURRENTLY hangs forever on a fresh database. */
    @Test
    void doesNotHoldATransactionOpenWhileMigrating() {
        FlywayConfig config = new FlywayConfig();
        ReflectionTestUtils.setField(config, "locations", new String[] {"classpath:db/migration"});

        Flyway flyway = config.flyway(mock(DataSource.class));

        PostgreSQLConfigurationExtension postgres = flyway.getConfiguration()
                .getPluginRegister()
                .getExact(PostgreSQLConfigurationExtension.class);
        assertThat(postgres.isTransactionalLock()).isFalse();
    }
}
