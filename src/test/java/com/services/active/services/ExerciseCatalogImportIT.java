package com.services.active.services;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.services.active.services.ExerciseCatalogImporter.Reason.*;
import static com.services.active.services.ExerciseCatalogPostgresFixture.EXERCISE;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class ExerciseCatalogImportIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");
    ExerciseCatalogPostgresFixture db;
    ExerciseCatalogImporter importer;
    ObjectNode first;
    ObjectNode second;

    @BeforeEach
    void setUp() throws Exception {
        db = new ExerciseCatalogPostgresFixture(POSTGRES, false);
        importer = db.importer();
        assertThat(AopUtils.isAopProxy(importer)).isTrue();
        first = db.record(0);
        second = db.record(1);
    }

    @AfterEach
    void tearDown() {
        if (db != null) db.close();
    }

    @Test
    void importCatalog_reviewedLegacyMatchAndMetadataUpdate_preserveIdentityAndHistory() {
        db.legacy(EXERCISE, first);
        db.references(EXERCISE);
        var history = db.history();

        var bound = importer.importCatalog(db.json(first));
        assertThat(bound.bound()).isEqualTo(1);
        assertThat(bound.inserted()).isZero();
        assertThat(bound.diagnostics()).isEmpty();
        assertThat(db.mappings()).containsOnly(entry(db.key(first), EXERCISE));
        db.assertReferences(EXERCISE, history);

        first.putArray("images").add("replacement/image-only-change.jpg");
        var repeated = importer.importCatalog(db.json(first));
        assertThat(repeated.inserted()).isZero();
        assertThat(repeated.bound()).isZero();
        assertThat(repeated.updated()).isZero();
        first.put("name", "Reviewed catalog metadata update");
        first.withArray("instructions").add("Additional reviewed instruction");
        var updated = importer.importCatalog(db.json(first));
        assertThat(updated.updated()).isEqualTo(1);
        assertThat(updated.inserted()).isZero();
        assertThat(db.mappings()).containsOnly(entry(db.key(first), EXERCISE));
        assertThat(db.jdbc.queryForObject("SELECT name FROM exercises WHERE id = ?", String.class, EXERCISE))
                .isEqualTo(first.path("name").asText());
        assertThat(db.jdbc.queryForObject("SELECT instructions[array_length(instructions, 1)] FROM exercises WHERE id = ?",
                String.class, EXERCISE)).isEqualTo("Additional reviewed instruction");
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM exercises", Integer.class)).isEqualTo(1);
        db.assertReferences(EXERCISE, history);
    }

    @ParameterizedTest(name = "reviewed legacy matching includes {0}")
    @CsvSource({
            "name,name,Custom legacy title,Custom legacy title",
            "level,level,EXPERT,expert",
            "force,force,PUSH,push",
            "mechanic,mechanic,ISOLATION,isolation",
            "equipment,equipment,BARBELL,barbell",
            "category,category,CARDIO,cardio",
            "primaryMuscles,primary_muscles,calves,calves",
            "secondaryMuscles,secondary_muscles,calves,calves",
            "instructions,instructions,Custom legacy instruction,Custom legacy instruction"
    })
    void importCatalog_oneStoredFieldDiffersFromReviewedManifest_defersEvenWhenIncomingMatchesLegacy(
            String field, String column, String storedValue, String sourceValue) {
        db.legacy(EXERCISE, first);
        if (first.path(field).isArray()) {
            db.jdbc.update("UPDATE exercises SET " + column + " = ARRAY[?]::text[] WHERE id = ?", storedValue, EXERCISE);
            first.putArray(field).add(sourceValue);
        } else {
            db.jdbc.update("UPDATE exercises SET " + column + " = ? WHERE id = ?", storedValue, EXERCISE);
            first.put(field, sourceValue);
        }
        db.references(EXERCISE);
        var history = db.history();
        String before = db.tableRows("exercises");

        var report = importer.importCatalog(db.json(first));

        assertThat(report.inserted()).isZero();
        assertThat(report.bound()).isZero();
        assertThat(report.updated()).isZero();
        assertThat(report.retired()).isZero();
        assertThat(report.diagnostics()).filteredOn(d -> d.reason() == LEGACY_METADATA_MISMATCH)
                .singleElement().satisfies(d -> {
                    assertThat(d.sourceKey()).isEqualTo(db.key(first));
                    assertThat(d.candidateIds()).containsExactly(EXERCISE);
                });
        assertThat(db.tableRows("exercises")).isEqualTo(before);
        db.assertReferences(EXERCISE, history);
    }

    @ParameterizedTest
    @ValueSource(strings = {"secondary_muscles = NULL",
            "instructions = ARRAY(SELECT value FROM unnest(instructions) WITH ORDINALITY AS items(value, position) ORDER BY position DESC)"})
    void importCatalog_nullVersusEmptyArrayAndReorderedInstructions_doNotBindLegacy(String change) {
        db.legacy(EXERCISE, first);
        db.jdbc.execute("UPDATE exercises SET " + change);
        String before = db.tableRows("exercises");

        var report = importer.importCatalog(db.json(first));

        assertThat(report.inserted()).isZero();
        assertThat(report.bound()).isZero();
        assertThat(report.diagnostics()).filteredOn(d -> d.reason() == LEGACY_METADATA_MISMATCH)
                .singleElement().satisfies(d -> assertThat(d.candidateIds()).containsExactly(EXERCISE));
        assertThat(db.tableRows("exercises")).isEqualTo(before);
    }

    @Test
    void importCatalog_unreviewedKeyWithExactLegacyMetadata_doesNotInventBinding() {
        db.legacy(EXERCISE, first);
        String before = db.tableRows("exercises");
        first.put("id", "unreviewed-source-key");

        var report = importer.importCatalog(db.json(first));

        assertThat(report.inserted()).isZero();
        assertThat(report.bound()).isZero();
        assertThat(report.diagnostics()).filteredOn(d -> d.reason() == LEGACY_METADATA_MISMATCH)
                .singleElement().satisfies(d -> {
                    assertThat(d.sourceKey()).isEqualTo("unreviewed-source-key");
                    assertThat(d.candidateIds()).containsExactly(EXERCISE);
                });
        assertThat(db.tableRows("exercises")).isEqualTo(before);
    }

    @Test
    void importCatalog_reviewedLegacyAndChangedIncomingMetadata_bindsReviewedIdentity() {
        db.legacy(EXERCISE, first);
        db.references(EXERCISE);
        var history = db.history();
        first.put("name", "Updated before first import");
        first.putArray("images").add("new/image.jpg");

        var report = importer.importCatalog(db.json(first));

        assertThat(report.bound()).isEqualTo(1);
        assertThat(report.inserted()).isZero();
        assertThat(report.diagnostics()).isEmpty();
        assertThat(db.mappings()).containsOnly(entry(db.key(first), EXERCISE));
        assertThat(db.jdbc.queryForObject("SELECT name FROM exercises WHERE id = ?", String.class, EXERCISE))
                .isEqualTo("Updated before first import");
        db.assertReferences(EXERCISE, history);
    }

    @Test
    void importCatalog_uncertainLegacyRows_reportCandidatesWithoutGuessingOrDuplicating() throws Exception {
        UUID duplicate = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UUID mismatch = UUID.fromString("00000000-0000-0000-0000-000000000103");
        UUID unrelated = UUID.fromString("00000000-0000-0000-0000-000000000104");
        db.legacy(EXERCISE, first);
        db.legacy(duplicate, first);
        ObjectNode custom = second.deepCopy();
        custom.putArray("instructions").add("Existing deployment custom instruction");
        db.legacy(mismatch, custom);
        db.legacy(unrelated, db.record(2));
        db.references(EXERCISE);
        var history = db.history();
        String before = db.tableRows("exercises");

        var report = importer.importCatalog(db.json(first, second));

        assertThat(report.inserted()).isZero();
        assertThat(report.bound()).isZero();
        assertThat(report.updated()).isZero();
        assertThat(report.retired()).isZero();
        assertThat(db.tableRows("exercises")).isEqualTo(before);
        assertThat(report.diagnostics()).filteredOn(d -> d.reason() == AMBIGUOUS_FINGERPRINT)
                .singleElement().satisfies(d -> {
                    assertThat(d.sourceKey()).isEqualTo(db.key(first));
                    assertThat(d.candidateIds()).containsExactlyInAnyOrder(EXERCISE, duplicate);
                });
        assertThat(report.diagnostics()).filteredOn(d -> d.reason() == LEGACY_METADATA_MISMATCH)
                .singleElement().satisfies(d -> {
                    assertThat(d.sourceKey()).isEqualTo(db.key(second));
                    assertThat(d.candidateIds()).containsExactly(mismatch);
                });
        assertThat(report.diagnostics()).filteredOn(d -> d.reason() == UNMATCHED_LEGACY)
                .flatExtracting(ExerciseCatalogImporter.Diagnostic::candidateIds)
                .containsExactlyInAnyOrder(EXERCISE, duplicate, mismatch, unrelated);
        db.assertReferences(EXERCISE, history);
    }

    @Test
    void importCatalog_missingThenRestoredKey_reactivatesOriginalRowWithHistory() {
        db.legacy(EXERCISE, first);
        db.references(EXERCISE);
        var history = db.history();
        importer.importCatalog(db.json(first, second));
        var mappings = db.mappings();

        assertThat(importer.importCatalog(db.json(second)).retired()).isEqualTo(1);
        assertThat(db.jdbc.queryForObject("SELECT retired FROM exercises WHERE id = ?", Boolean.class, EXERCISE)).isTrue();
        db.assertReferences(EXERCISE, history);
        var restored = importer.importCatalog(db.json(first, second));
        assertThat(restored.reactivated()).isEqualTo(1);
        assertThat(restored.inserted()).isZero();
        assertThat(db.jdbc.queryForObject("SELECT retired FROM exercises WHERE id = ?", Boolean.class, EXERCISE)).isFalse();
        assertThat(db.mappings()).isEqualTo(mappings);
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM exercises", Integer.class)).isEqualTo(2);
        db.assertReferences(EXERCISE, history);
    }

    @Test
    void importCatalog_possibleSourceKeyRename_preservesOldIdentityAndReportsCandidate() {
        db.legacy(EXERCISE, first);
        db.references(EXERCISE);
        var history = db.history();
        importer.importCatalog(db.json(first));
        String oldKey = db.key(first);
        first.put("id", "replacement-source-key");

        var report = importer.importCatalog(db.json(first));

        assertThat(report.inserted()).isZero();
        assertThat(report.bound()).isZero();
        assertThat(report.retired()).isEqualTo(1);
        assertThat(report.diagnostics()).singleElement().satisfies(d -> {
            assertThat(d.reason()).isEqualTo(POSSIBLE_KEY_RENAME);
            assertThat(d.sourceKey()).isEqualTo("replacement-source-key");
            assertThat(d.candidateIds()).containsExactly(EXERCISE);
        });
        assertThat(db.mappings()).containsOnly(entry(oldKey, EXERCISE));
        db.assertReferences(EXERCISE, history);
    }

    @Test
    void importCatalog_invalidSnapshotsAndDatabaseFlushFailure_rollBackAllChanges() throws Exception {
        db.legacy(EXERCISE, first);
        db.references(EXERCISE);
        importer.importCatalog(db.json(first, second));
        var history = db.history();
        String before = db.tableRows("exercises");
        first.put("name", "forced catalog failure");
        assertThatThrownBy(() -> importer.importCatalog(db.json(first, first)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate source key");
        assertThatThrownBy(() -> importer.importCatalog("[]"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nonempty");
        assertThat(db.tableRows("exercises")).isEqualTo(before);

        // Fail a real PostgreSQL write after Hibernate has inserted a new catalog row.
        ObjectNode newRecord = db.record(2);
        db.jdbc.execute("""
                CREATE FUNCTION reject_catalog_update() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM exercises WHERE name = 'rollback insertion witness') THEN
                        RAISE EXCEPTION 'missing prior insertion witness';
                    END IF;
                    RAISE EXCEPTION 'forced catalog write rejection' USING ERRCODE = '23514';
                END $$
                """);
        db.jdbc.execute("""
                CREATE TRIGGER reject_catalog_update BEFORE UPDATE ON exercises FOR EACH ROW
                WHEN (NEW.name = 'forced catalog failure') EXECUTE FUNCTION reject_catalog_update()
                """);
        newRecord.put("name", "rollback insertion witness");
        try {
            // Also omits the second key: insert, update, and retirement must all roll back.
            assertThatThrownBy(() -> importer.importCatalog(db.json(first, newRecord)))
                    .isInstanceOf(RuntimeException.class).hasStackTraceContaining("forced catalog write rejection");
            assertThat(db.tableRows("exercises")).isEqualTo(before);
            db.assertReferences(EXERCISE, history);
        } finally {
            db.jdbc.execute("DROP TRIGGER reject_catalog_update ON exercises");
            db.jdbc.execute("DROP FUNCTION reject_catalog_update()");
        }
        first = db.record(0);
        var retry = importer.importCatalog(db.json(first, second));
        assertThat(retry.inserted()).isZero();
        assertThat(retry.updated()).isZero();
        assertThat(db.tableRows("exercises")).isEqualTo(before);
    }

    @Test
    void importPinnedCatalog_repeatedImport_keepsAll876MappingsStable() throws Exception {
        assertThat(importer.importPinnedCatalog().inserted()).isEqualTo(876);
        var mappings = db.mappings();
        assertThat(mappings).hasSize(876);
        List<String> sourceKeys = new java.util.ArrayList<>();
        db.mapper.readTree(db.catalog.pinnedJson()).forEach(row -> sourceKeys.add(row.path("id").asText()));
        assertThat(mappings.keySet()).containsExactlyInAnyOrderElementsOf(sourceKeys);
        var repeated = importer.importPinnedCatalog();
        assertThat(repeated.inserted()).isZero();
        assertThat(repeated.updated()).isZero();
        assertThat(repeated.diagnostics()).isEmpty();
        assertThat(db.mappings()).isEqualTo(mappings);
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM exercises", Integer.class)).isEqualTo(876);
    }

    @Test
    @Timeout(value = 75, unit = TimeUnit.SECONDS)
    void importCatalog_twoConcurrentProxiedCallsOnEmptyDatabase_serializeAndKeepOneMappingPerKey() throws Exception {
        String snapshot = db.json(first, second);
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try (Connection blocker = db.dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.createStatement()) {
                statement.execute("SELECT pg_advisory_xact_lock(714876001)");
            }
            try {
                var a = executor.submit(() -> { start.await(); return importer.importCatalog(snapshot); });
                var b = executor.submit(() -> { start.await(); return importer.importCatalog(snapshot); });
                start.countDown();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                Integer waiting;
                do {
                    waiting = db.jdbc.queryForObject("""
                            SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND NOT granted
                            AND classid = 0 AND objid = 714876001 AND objsubid = 1
                            AND database = (SELECT oid FROM pg_database WHERE datname = current_database())
                            """, Integer.class);
                    if (waiting == 2) break;
                    Thread.sleep(50);
                } while (System.nanoTime() < deadline);
                assertThat(waiting).as("both importer transactions wait on the database advisory lock").isEqualTo(2);
                assertThat(db.mappings()).isEmpty();
                blocker.commit();
                var firstReport = a.get(15, TimeUnit.SECONDS);
                var secondReport = b.get(15, TimeUnit.SECONDS);
                assertThat(List.of(firstReport.inserted(), secondReport.inserted())).containsExactlyInAnyOrder(2, 0);
                assertThat(firstReport.diagnostics()).isEmpty();
                assertThat(secondReport.diagnostics()).isEmpty();
                var mappings = db.mappings();
                assertThat(mappings).hasSize(2).containsKeys(db.key(first), db.key(second));
                assertThat(mappings.values()).doesNotHaveDuplicates();
                assertThat(db.jdbc.queryForObject("SELECT count(*) FROM exercises", Integer.class)).isEqualTo(2);
                assertThat(importer.importCatalog(snapshot).inserted()).isZero();
                assertThat(db.mappings()).isEqualTo(mappings);
            } finally {
                blocker.rollback();
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).as("import threads terminate").isTrue();
        }
    }
}
