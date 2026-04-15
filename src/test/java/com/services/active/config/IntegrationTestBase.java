package com.services.active.config;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import({TestPostgresConfig.class, TestWorkosConfig.class, TestSecurityConfig.class})
public abstract class IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUpDatabase() {
        // Preserve users/exercises so TestUserContext remains valid across tests.
        jdbcTemplate.execute("""
            DELETE FROM exercise_records;
            DELETE FROM workout_records;
            DELETE FROM routine_patterns;
            DELETE FROM routines;
            DELETE FROM template_exercises;
            DELETE FROM workouts;
            DELETE FROM workout_templates;
            DELETE FROM exercise_personal_bests;
            DELETE FROM user_weekly_completed_workouts;
            DELETE FROM user_notification_schedule;
            DELETE FROM user_push_tokens;
        """);
    }
}
