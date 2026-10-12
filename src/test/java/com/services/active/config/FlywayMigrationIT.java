package com.services.active.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FlywayMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = TestPostgresConfig.postgresContainer();

    @Test
    void migrate_whenV1ContainsData_shouldApplyV2WithoutDataLoss() throws Exception {
        flyway().target("1").load().migrate();

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (workos_id) VALUES ('existing-user')");
        }

        flyway().load().migrate();

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT workos_id FROM users")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("existing-user");
            }
            try (var result = statement.executeQuery("SELECT version FROM flyway_schema_history ORDER BY installed_rank")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("1");
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("2");
            }
            try (var result = statement.executeQuery("SELECT count(*) FROM idempotency_keys")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isZero();
            }
        }
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration flyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
    }
}
