package com.services.active.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.services.active.models.Exercise;
import com.services.active.repository.ExerciseRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExerciseCatalogImporterTest {
    @Mock ExerciseRepository repository;
    @Mock EntityManager entityManager;
    @Mock Query lockQuery;
    final ExerciseCatalog catalog = new ExerciseCatalog();
    final ObjectMapper mapper = new ObjectMapper();
    final List<Exercise> rows = new ArrayList<>();
    ExerciseCatalogImporter importer;
    ObjectNode first;
    ObjectNode second;

    @BeforeEach
    void setUp() throws Exception {
        importer = new ExerciseCatalogImporter(repository, catalog, entityManager);
        ArrayNode pinned = (ArrayNode) mapper.readTree(catalog.pinnedJson());
        first = (ObjectNode) pinned.get(0).deepCopy();
        second = (ObjectNode) pinned.get(1).deepCopy();
        lenient().when(entityManager.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(714876001)")).thenReturn(lockQuery);
        lenient().when(repository.findAll()).thenAnswer(invocation -> new ArrayList<>(rows));
        lenient().when(repository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Exercise> writes = invocation.getArgument(0);
            for (Exercise row : writes) {
                if (rows.stream().noneMatch(e -> e.getId().equals(row.getId()))) rows.add(row);
            }
            return writes;
        });
    }

    @Test
    void importCatalog_repeatedSnapshot_preservesUuidAndCount() {
        var initial = importer.importCatalog(json(first));
        UUID id = rows.getFirst().getId();
        var repeated = importer.importCatalog(json(first));
        assertThat(initial.inserted()).isEqualTo(1);
        assertThat(repeated.inserted()).isZero();
        assertThat(repeated.updated()).isZero();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getId()).isEqualTo(id);
        verify(lockQuery, times(2)).getSingleResult();
    }

    @Test
    void importCatalog_metadataRename_preservesUuid() {
        importer.importCatalog(json(first));
        UUID id = rows.getFirst().getId();
        first.put("name", "Renamed exercise");
        first.withArray("instructions").add("Additional instruction");
        var report = importer.importCatalog(json(first));
        assertThat(report.updated()).isEqualTo(1);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getId()).isEqualTo(id);
        assertThat(rows.getFirst().getName()).isEqualTo("Renamed exercise");
        assertThat(rows.getFirst().getInstructions()).contains("Additional instruction");
    }

    @Test
    void importCatalog_missingThenRestored_retiresAndReactivatesSameUuid() {
        importer.importCatalog(json(first, second));
        Exercise original = rows.getFirst();
        UUID id = original.getId();
        assertThat(importer.importCatalog(json(second)).retired()).isEqualTo(1);
        assertThat(original.isRetired()).isTrue();
        assertThat(rows).hasSize(2);
        assertThat(importer.importCatalog(json(first, second)).reactivated()).isEqualTo(1);
        assertThat(original.isRetired()).isFalse();
        assertThat(original.getId()).isEqualTo(id);
        verify(repository, never()).delete(any(Exercise.class));
    }

    @Test
    void importCatalog_duplicateKeys_prevalidatesBeforeAnyDatabaseInteraction() {
        importer.importCatalog(json(first));
        Exercise original = rows.getFirst();
        clearInvocations(repository, entityManager, lockQuery);
        first.put("name", "Must not be applied");
        assertThatThrownBy(() -> importer.importCatalog(json(first, first)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate source key");
        assertThat(original.getName()).isNotEqualTo("Must not be applied");
        verifyNoInteractions(repository, entityManager, lockQuery);
    }

    @Test
    void importCatalog_malformedMetadataAndEmptySnapshots_prevalidate() {
        List<String> invalid = new ArrayList<>(List.of("[]", "null", "{}", "broken"));
        for (String field : List.of("id", "name", "level", "category")) {
            ObjectNode copy = first.deepCopy();
            copy.put(field, " ");
            invalid.add(json(copy));
        }
        for (String field : List.of("primaryMuscles", "secondaryMuscles", "instructions", "images")) {
            ObjectNode copy = first.deepCopy();
            copy.putNull(field);
            invalid.add(json(copy));
        }
        ObjectNode unknownEnum = first.deepCopy();
        unknownEnum.put("equipment", "made up");
        invalid.add(json(unknownEnum));
        ObjectNode wrongType = first.deepCopy();
        wrongType.withArray("instructions").add(3);
        invalid.add(json(wrongType));
        for (String json : invalid) assertThatThrownBy(() -> importer.importCatalog(json))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, entityManager, lockQuery);
    }

    @Test
    void importCatalog_exactReviewedLegacyMatch_bindsInPlaceAndUpdatesMetadata() {
        Exercise legacy = legacy(first);
        UUID id = legacy.getId();
        first.put("name", "Updated after reviewed snapshot");
        var report = importer.importCatalog(json(first));
        assertThat(report.bound()).isEqualTo(1);
        assertThat(report.inserted()).isZero();
        assertThat(report.diagnostics()).isEmpty();
        assertThat(rows).hasSize(1);
        assertThat(legacy.getId()).isEqualTo(id);
        assertThat(legacy.getSourceKey()).isEqualTo(first.path("id").asText());
        assertThat(legacy.getName()).isEqualTo("Updated after reviewed snapshot");
    }

    @Test
    void importCatalog_duplicateLegacyFingerprints_leavesBothUntouchedAndReportsIds() {
        Exercise a = legacy(first);
        Exercise b = legacy(first);
        var report = importer.importCatalog(json(first));
        assertThat(report.inserted()).isZero();
        assertThat(report.bound()).isZero();
        assertThat(rows).hasSize(2).allMatch(e -> e.getSourceKey() == null && !e.isRetired());
        assertThat(report.diagnostics()).anySatisfy(d -> {
            assertThat(d.reason()).isEqualTo(ExerciseCatalogImporter.Reason.AMBIGUOUS_FINGERPRINT);
            assertThat(d.candidateIds()).containsExactlyInAnyOrder(a.getId(), b.getId());
        });
    }

    @Test
    void importCatalog_sameNameDifferentFields_defersAndKeepsLegacyMetadata() {
        Exercise legacy = legacy(first);
        legacy.setInstructions(List.of("Legacy deployment custom instruction"));
        var report = importer.importCatalog(json(first));
        assertThat(report.inserted()).isZero();
        assertThat(legacy.getSourceKey()).isNull();
        assertThat(legacy.getInstructions()).containsExactly("Legacy deployment custom instruction");
        assertThat(report.diagnostics()).extracting(ExerciseCatalogImporter.Diagnostic::reason)
                .contains(ExerciseCatalogImporter.Reason.LEGACY_METADATA_MISMATCH,
                        ExerciseCatalogImporter.Reason.UNMATCHED_LEGACY);
    }

    @Test
    void importCatalog_renamedKeyExactMappedFields_defersAndRetainsOldUuid() {
        importer.importCatalog(json(first));
        UUID id = rows.getFirst().getId();
        String oldKey = rows.getFirst().getSourceKey();
        first.put("id", "renamed-source-key");
        var report = importer.importCatalog(json(first));
        assertThat(report.inserted()).isZero();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getId()).isEqualTo(id);
        assertThat(rows.getFirst().getSourceKey()).isEqualTo(oldKey);
        assertThat(rows.getFirst().isRetired()).isTrue();
        assertThat(report.diagnostics()).singleElement().satisfies(d ->
                assertThat(d.reason()).isEqualTo(ExerciseCatalogImporter.Reason.POSSIBLE_KEY_RENAME));
    }

    @Test
    void importCatalog_newKeyWithoutReviewedFingerprint_doesNotBindExactUnmappedLegacy() {
        Exercise legacy = legacy(first);
        first.put("id", "new-key");
        var report = importer.importCatalog(json(first));
        assertThat(report.inserted()).isZero();
        assertThat(report.bound()).isZero();
        assertThat(legacy.getSourceKey()).isNull();
        assertThat(rows).hasSize(1);
    }

    @Test
    void importPinnedCatalog_fullFixture_matchesEveryPythonFingerprintAndRepeats() {
        var parsed = catalog.parse(catalog.pinnedJson());
        assertThat(parsed).hasSize(876);
        assertThat(parsed.entrySet()).allSatisfy(entry ->
                assertThat(catalog.fingerprint(entry.getValue())).isEqualTo(catalog.legacyFingerprint(entry.getKey())));
        assertThat(parsed.values().stream().map(catalog::fingerprint).distinct().count()).isEqualTo(876);
        assertThat(importer.importPinnedCatalog().inserted()).isEqualTo(876);
        Set<UUID> ids = new HashSet<>(rows.stream().map(Exercise::getId).toList());
        assertThat(importer.importPinnedCatalog().inserted()).isZero();
        assertThat(rows).hasSize(876);
        assertThat(rows.stream().map(Exercise::getId)).containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    void fingerprint_nullArraysAndArrayOrder_areNotNormalized() {
        Exercise exercise = catalog.parse(json(first)).values().iterator().next();
        exercise.setInstructions(null);
        String nullHash = catalog.fingerprint(exercise);
        exercise.setInstructions(List.of());
        assertThat(catalog.fingerprint(exercise)).isNotEqualTo(nullHash);
        exercise.setInstructions(List.of("one", "two"));
        String ordered = catalog.fingerprint(exercise);
        exercise.setInstructions(List.of("two", "one"));
        assertThat(catalog.fingerprint(exercise)).isNotEqualTo(ordered);
    }

    @Test
    void importCatalog_unrelatedLegacyAndTrulyNewKey_insertsAndReportsWithoutChangingLegacy() {
        Exercise unrelated = legacy(second);
        UUID id = unrelated.getId();
        first.put("id", "truly-new-key");
        var report = importer.importCatalog(json(first));
        assertThat(report.inserted()).isEqualTo(1);
        assertThat(rows).hasSize(2);
        assertThat(unrelated.getId()).isEqualTo(id);
        assertThat(unrelated.getSourceKey()).isNull();
        assertThat(unrelated.isRetired()).isFalse();
        assertThat(report.diagnostics()).singleElement().satisfies(d -> {
            assertThat(d.reason()).isEqualTo(ExerciseCatalogImporter.Reason.UNMATCHED_LEGACY);
            assertThat(d.candidateIds()).containsExactly(id);
        });
    }

    @Test
    void exerciseJson_identityColumns_doNotChangeUuidResponseShape() {
        importer.importCatalog(json(first));
        Exercise exercise = rows.getFirst();
        exercise.setRetired(true);
        var response = tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(exercise);
        assertThat(response.path("id").asText()).isEqualTo(exercise.getId().toString());
        assertThat(response.has("sourceKey")).isFalse();
        assertThat(response.has("retired")).isFalse();
        assertThat(response.path("name").asText()).isEqualTo(exercise.getName());
    }

    private Exercise legacy(ObjectNode node) {
        Exercise row = catalog.parse(json(node)).values().iterator().next();
        row.setSourceKey(null);
        row.setId(UUID.randomUUID());
        rows.add(row);
        return row;
    }

    private String json(ObjectNode... records) {
        ArrayNode array = mapper.createArrayNode();
        for (ObjectNode record : records) array.add(record);
        return array.toString();
    }
}
