package com.services.active.services;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static com.services.active.services.ExerciseCatalogPostgresFixture.EXERCISE;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class ExerciseCatalogMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Test
    void migrate_v2WithLegacyHistory_thenV3V4AndImport_preserveRowsAndForeignKeys() throws Exception {
        try (var db = new ExerciseCatalogPostgresFixture(POSTGRES, true)) {
            var record = db.record(0);
            db.legacy(EXERCISE, record);
            db.references(EXERCISE);
            var history = db.history();
            String legacyMetadata = db.jdbc.queryForObject(
                    "SELECT to_jsonb(e)::text FROM exercises e WHERE id = ?", String.class, EXERCISE);
            var checksums = db.jdbc.queryForList(
                    "SELECT version, checksum FROM flyway_schema_history ORDER BY installed_rank");
            assertThat(db.jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                    String.class)).containsExactly("1", "2");

            var importer = db.importer();

            assertThat(db.jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                    String.class)).containsExactly("1", "2", "3", "4");
            assertThat(db.jdbc.queryForList(
                    "SELECT version, checksum FROM flyway_schema_history WHERE version IN ('1', '2') ORDER BY installed_rank"))
                    .isEqualTo(checksums);
            assertThat(db.jdbc.queryForObject(
                    "SELECT (to_jsonb(e) - 'source_key' - 'retired')::text FROM exercises e WHERE id = ?", String.class, EXERCISE))
                    .isEqualTo(legacyMetadata);
            assertThat(db.jdbc.queryForObject("SELECT source_key FROM exercises WHERE id = ?", String.class, EXERCISE)).isNull();
            assertThat(db.jdbc.queryForObject("SELECT retired FROM exercises WHERE id = ?", Boolean.class, EXERCISE)).isFalse();
            assertThat(db.jdbc.queryForObject("SELECT profile_version FROM users", Long.class)).isZero();
            assertThat(db.jdbc.queryForObject("SELECT row_version FROM users", Long.class)).isZero();
            db.assertReferences(EXERCISE, history);
            assertThat(importer.importCatalog(db.json(record)).bound()).isEqualTo(1);
            assertThat(importer.importCatalog(db.json(record)).inserted()).isZero();
            record.put("name", "Metadata updated after V4 migration");
            assertThat(importer.importCatalog(db.json(record)).updated()).isEqualTo(1);
            assertThat(db.mappings()).containsOnly(entry(db.key(record), EXERCISE));
            assertThat(db.jdbc.queryForObject("SELECT count(*) FROM exercises", Integer.class)).isEqualTo(1);
            db.assertReferences(EXERCISE, history);
        }
    }

    @Test
    void migrate_freshProductionSchema_validatesHibernateAndEnforcesCatalogConstraints() {
        try (var db = new ExerciseCatalogPostgresFixture(POSTGRES, false)) {
            // Context refresh builds the actual entity mappings with hbm2ddl.auto=validate.
            assertThat(AopUtils.isAopProxy(db.importer())).isTrue();
            assertThat(db.jdbc.queryForObject("SELECT count(*) FROM exercises", Integer.class)).isZero();
            db.jdbc.update("INSERT INTO exercises (id, name) VALUES (?, 'Legacy A'), (?, 'Legacy B')",
                    EXERCISE, UUID.fromString("00000000-0000-0000-0000-000000000102"));
            assertThat(db.jdbc.queryForObject("SELECT count(*) FROM exercises WHERE source_key IS NULL AND retired = false",
                    Integer.class)).isEqualTo(2);
            UUID mapped = UUID.fromString("00000000-0000-0000-0000-000000000103");
            db.jdbc.update("INSERT INTO exercises (id, name, source_key) VALUES (?, 'Mapped', 'unique-catalog-key')", mapped);
            assertThatThrownBy(() -> db.jdbc.update(
                    "INSERT INTO exercises (id, name, source_key) VALUES (?, 'Duplicate', 'unique-catalog-key')", UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasStackTraceContaining("exercises_source_key_unique");
            assertThatThrownBy(() -> db.jdbc.update("UPDATE exercises SET source_key = 'unique-catalog-key' WHERE id = ?", EXERCISE))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> db.jdbc.update("UPDATE exercises SET retired = NULL WHERE id = ?", mapped))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(db.jdbc.queryForObject("SELECT count(*) FROM exercises", Integer.class)).isEqualTo(3);
            assertThat(db.jdbc.queryForObject("SELECT count(*) FROM exercises WHERE source_key IS NULL", Integer.class)).isEqualTo(2);
        }
    }
}
