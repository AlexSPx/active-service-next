package com.services.active.config;

import com.services.active.models.IdempotencyReceipt;
import com.services.active.models.Routine;
import com.services.active.models.Workout;
import com.services.active.models.user.User;
import org.flywaydb.core.Flyway;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FlywayMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = TestPostgresConfig.postgresContainer();

    @Test
    void migrate_whenV2ContainsData_shouldApplyEntityVersionsWithoutDataLoss() throws Exception {
        flyway().target("2").load().migrate();

        UUID userId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID workoutId = UUID.randomUUID();
        UUID routineId = UUID.randomUUID();
        Map<String, Integer> originalChecksums = new HashMap<>();

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (id, workos_id, username, timezone, weight_kg, height_cm)
                    VALUES ('%s', 'existing-user', 'existing-name', 'Europe/Sofia', 72.5, 181)
                    """.formatted(userId));
            statement.executeUpdate("INSERT INTO workout_templates (id) VALUES ('%s')".formatted(templateId));
            statement.executeUpdate("""
                    INSERT INTO workouts (id, user_id, title, notes, template_id)
                    VALUES ('%s', '%s', 'existing-workout', 'existing-notes', '%s')
                    """.formatted(workoutId, userId, templateId));
            statement.executeUpdate("""
                    INSERT INTO routines (id, user_id, name, description)
                    VALUES ('%s', '%s', 'existing-routine', 'existing-description')
                    """.formatted(routineId, userId));
            statement.executeUpdate("UPDATE users SET active_routine_id = '%s' WHERE id = '%s'"
                    .formatted(routineId, userId));
            statement.executeUpdate("""
                    INSERT INTO idempotency_keys (owner_id, operation_id, fingerprint, response_body)
                    VALUES ('existing-user', 'pending-operation', 'old-fingerprint', NULL)
                    """);
            try (var result = statement.executeQuery("""
                    SELECT version, checksum FROM flyway_schema_history
                    WHERE version IN ('1', '2') ORDER BY installed_rank
                    """)) {
                while (result.next()) {
                    originalChecksums.put(result.getString("version"), result.getInt("checksum"));
                }
            }
        }

        flyway().load().migrate();

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("""
                    SELECT workos_id, username, timezone, weight_kg, height_cm,
                           active_routine_id, profile_version, row_version
                    FROM users WHERE id = '%s'
                    """.formatted(userId))) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("workos_id")).isEqualTo("existing-user");
                assertThat(result.getString("username")).isEqualTo("existing-name");
                assertThat(result.getString("timezone")).isEqualTo("Europe/Sofia");
                assertThat(result.getDouble("weight_kg")).isEqualTo(72.5);
                assertThat(result.getInt("height_cm")).isEqualTo(181);
                assertThat(result.getObject("active_routine_id", UUID.class)).isEqualTo(routineId);
                assertThat(result.getLong("profile_version")).isZero();
                assertThat(result.getLong("row_version")).isZero();
            }
            try (var result = statement.executeQuery(
                    "SELECT title, notes, version FROM workouts WHERE id = '%s'".formatted(workoutId))) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("title")).isEqualTo("existing-workout");
                assertThat(result.getString("notes")).isEqualTo("existing-notes");
                assertThat(result.getLong("version")).isZero();
            }
            try (var result = statement.executeQuery(
                    "SELECT name, description, version FROM routines WHERE id = '%s'".formatted(routineId))) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("name")).isEqualTo("existing-routine");
                assertThat(result.getString("description")).isEqualTo("existing-description");
                assertThat(result.getLong("version")).isZero();
            }
            try (var result = statement.executeQuery("""
                    SELECT fingerprint, response_body FROM idempotency_keys
                    WHERE owner_id = 'existing-user' AND operation_id = 'pending-operation'
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("fingerprint")).isEqualTo("old-fingerprint");
                assertThat(result.getString("response_body")).isNull();
            }
            Map<String, Integer> migratedChecksums = new HashMap<>();
            try (var result = statement.executeQuery("""
                    SELECT version, checksum FROM flyway_schema_history
                    WHERE version IN ('1', '2') ORDER BY installed_rank
                    """)) {
                while (result.next()) {
                    migratedChecksums.put(result.getString("version"), result.getInt("checksum"));
                }
            }
            assertThat(migratedChecksums).isEqualTo(originalChecksums);
        }

        try (var ignored = new Configuration()
                .addAnnotatedClass(IdempotencyReceipt.class)
                .addAnnotatedClass(User.class)
                .addAnnotatedClass(Workout.class)
                .addAnnotatedClass(Routine.class)
                .setProperty("jakarta.persistence.jdbc.url", POSTGRES.getJdbcUrl())
                .setProperty("jakarta.persistence.jdbc.user", POSTGRES.getUsername())
                .setProperty("jakarta.persistence.jdbc.password", POSTGRES.getPassword())
                .setProperty(
                        "hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .buildSessionFactory()) {
        }
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration flyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
    }
}
