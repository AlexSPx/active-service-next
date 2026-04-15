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
            DO $$ DECLARE
                r RECORD;
            BEGIN
                FOR r IN (
                    SELECT tablename
                    FROM pg_tables
                    WHERE schemaname = 'public'
                      AND tablename NOT IN ('flyway_schema_history', 'users', 'exercises')
                ) LOOP
                    EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' CASCADE';
                END LOOP;
            END $$;
        """);
    }
}
