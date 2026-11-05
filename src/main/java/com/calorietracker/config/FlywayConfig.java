package com.calorietracker.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
public class FlywayConfig {

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String[] locations;

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                // V7 uses CREATE INDEX CONCURRENTLY, which waits for every open transaction to
                // finish. Flyway's default lock keeps a transaction open for the whole run, so V7
                // would wait on Flyway itself forever and a fresh database would never start.
                // A session-level lock still stops two instances migrating at once, with no open
                // transaction. Do not fix this by editing V7: its checksum is already recorded in
                // migrated databases, and Flyway refuses to start when it changes.
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .load();
    }
}
