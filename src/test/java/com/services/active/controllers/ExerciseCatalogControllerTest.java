package com.services.active.controllers;

import com.services.active.config.CookieTokenFilter;
import com.services.active.config.SecurityConfig;
import com.services.active.models.Exercise;
import com.services.active.models.types.*;
import com.services.active.repository.ExerciseRepository;
import com.services.active.services.ExerciseCatalog;
import com.services.active.services.ExerciseCatalogSnapshotService;
import com.services.active.services.ExerciseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExerciseController.class)
@ActiveProfiles("catalog-endpoint-test")
@Import({SecurityConfig.class, CookieTokenFilter.class, ExerciseCatalogSnapshotService.class, ExerciseCatalog.class})
class ExerciseCatalogControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ExerciseCatalog catalog;
    @MockitoBean ExerciseService exerciseService;
    @MockitoBean ExerciseRepository repository;
    @MockitoBean JwtDecoder jwtDecoder;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void catalog_withoutAuthentication_returns401WithoutReading() throws Exception {
        mvc.perform(get("/api/exercises/catalog")).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository, exerciseService);
    }

    @Test
    void catalog_returnsEntireCollectionIncludingRetiredAndLegacyWithoutSearchOrWrites() throws Exception {
        Exercise mapped = row();
        mapped.setRetired(true);
        Exercise legacy = row();
        legacy.setId(new UUID(0, 2));
        legacy.setSourceKey(null);
        legacy.setLevel(null);
        legacy.setForce(null);
        legacy.setMechanic(null);
        legacy.setEquipment(null);
        legacy.setPrimaryMuscles(null);
        legacy.setSecondaryMuscles(null);
        legacy.setInstructions(null);
        legacy.setCategory(null);
        when(repository.findAll()).thenReturn(List.of(legacy, mapped));

        JsonNode response = snapshot("?name=does-not-match&category=CARDIO&page=0&size=1");
        assertThat(response.path("items").size()).isEqualTo(2);
        JsonNode first = response.path("items").get(0);
        assertThat(first.path("id").asText()).isEqualTo(mapped.getId().toString());
        assertThat(first.path("sourceKey").asText()).isEqualTo(mapped.getSourceKey());
        assertThat(first.path("retired").asBoolean()).isTrue();
        assertThat(first.path("name").asText()).isEqualTo(mapped.getName());
        assertThat(first.path("level").asText()).isEqualTo(mapped.getLevel().name());
        assertThat(first.path("force").asText()).isEqualTo(mapped.getForce().name());
        assertThat(first.path("mechanic").asText()).isEqualTo(mapped.getMechanic().name());
        assertThat(first.path("equipment").asText()).isEqualTo(mapped.getEquipment().name());
        assertThat(first.path("primaryMuscles")).isEqualTo(mapper.valueToTree(mapped.getPrimaryMuscles()));
        assertThat(first.path("secondaryMuscles")).isEqualTo(mapper.valueToTree(mapped.getSecondaryMuscles()));
        assertThat(first.path("instructions")).isEqualTo(mapper.valueToTree(mapped.getInstructions()));
        assertThat(first.path("category").asText()).isEqualTo(mapped.getCategory().name());
        assertThat(first.size()).isEqualTo(12);
        JsonNode legacyItem = response.path("items").get(1);
        assertThat(legacyItem.path("id").asText()).isEqualTo(legacy.getId().toString());
        assertThat(legacyItem.path("retired").asBoolean()).isFalse();
        for (String field : List.of("sourceKey", "level", "force", "mechanic", "equipment", "primaryMuscles",
                "secondaryMuscles", "instructions", "category")) {
            assertThat(legacyItem.has(field)).as("legacy field %s is explicit", field).isTrue();
            assertThat(legacyItem.path(field).isNull()).as("legacy field %s is null", field).isTrue();
        }
        assertThat(response.path("unresolvedLegacyIds").get(0).asText()).isEqualTo(legacy.getId().toString());
        assertThat(response.path("mappingComplete").asBoolean()).isFalse();
        assertThat(response.path("sourceDatasetVersion").asText()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(catalog.pinnedJson().getBytes(StandardCharsets.UTF_8))));
        assertThat(response.path("catalogVersion").asText()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(response.path("items")))));
        verify(repository).findAll();
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(exerciseService);
    }

    @Test
    void catalog_fullPinnedMapping_isCompleteButMissingRetiredAndLegacyMappingsArePending() throws Exception {
        var rows = new ArrayList<>(catalog.parse(catalog.pinnedJson()).values());
        rows.forEach(row -> row.setId(UUID.nameUUIDFromBytes(row.getSourceKey().getBytes(StandardCharsets.UTF_8))));
        when(repository.findAll()).thenAnswer(invocation -> new ArrayList<>(rows));
        JsonNode full = snapshot("");
        assertThat(full.path("mappingComplete").asBoolean()).isTrue();
        assertThat(full.path("unresolvedSourceKeys").isEmpty()).isTrue();
        assertThat(full.path("retiredSourceKeys").isEmpty()).isTrue();
        assertThat(full.path("unresolvedLegacyIds").isEmpty()).isTrue();
        assertThat(full.path("items").size()).isEqualTo(rows.size());

        Exercise removed = rows.removeFirst();
        JsonNode missing = snapshot("");
        assertThat(missing.path("mappingComplete").asBoolean()).isFalse();
        assertThat(missing.path("unresolvedSourceKeys").get(0).asText()).isEqualTo(removed.getSourceKey());
        rows.add(removed);
        removed.setRetired(true);
        JsonNode retired = snapshot("");
        assertThat(retired.path("mappingComplete").asBoolean()).isFalse();
        assertThat(retired.path("unresolvedSourceKeys").isEmpty()).isTrue();
        assertThat(retired.path("retiredSourceKeys").get(0).asText()).isEqualTo(removed.getSourceKey());
        removed.setRetired(false);
        Exercise legacy = row();
        legacy.setSourceKey(null);
        legacy.setRetired(true);
        rows.add(legacy);
        JsonNode unmatched = snapshot("");
        assertThat(unmatched.path("mappingComplete").asBoolean()).isFalse();
        assertThat(unmatched.path("unresolvedLegacyIds").get(0).asText()).isEqualTo(legacy.getId().toString());
        legacy.setSourceKey("future-key");
        legacy.setRetired(true);
        assertThat(snapshot("").path("mappingComplete").asBoolean()).isTrue();
    }

    @Test
    void catalog_unchangedDataInDifferentRepositoryOrder_hasSameVersion() throws Exception {
        Exercise a = row();
        Exercise b = row();
        b.setId(new UUID(0, 2));
        b.setSourceKey(null);
        when(repository.findAll()).thenReturn(List.of(a, b), List.of(b, a));
        assertThat(snapshot("")).isEqualTo(snapshot(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "sourceKey", "retired", "name", "level", "force", "mechanic", "equipment",
            "primaryMuscles", "secondaryMuscles", "instructions", "category", "membership", "addition"})
    void catalog_identityMetadataRetirementOrMembershipChange_changesVersion(String field) throws Exception {
        Exercise row = row();
        var rows = new ArrayList<>(List.of(row));
        when(repository.findAll()).thenAnswer(invocation -> new ArrayList<>(rows));
        JsonNode before = snapshot("");
        switch (field) {
            case "id" -> row.setId(new UUID(0, 3));
            case "sourceKey" -> row.setSourceKey("new-key");
            case "retired" -> row.setRetired(true);
            case "name" -> row.setName("New name");
            case "level" -> row.setLevel(row.getLevel() == Level.EXPERT ? Level.BEGINNER : Level.EXPERT);
            case "force" -> row.setForce(row.getForce() == ForceType.PUSH ? ForceType.PULL : ForceType.PUSH);
            case "mechanic" -> row.setMechanic(row.getMechanic() == MechanicType.COMPOUND ? MechanicType.ISOLATION : MechanicType.COMPOUND);
            case "equipment" -> row.setEquipment(row.getEquipment() == Equipment.BARBELL ? Equipment.DUMBBELL : Equipment.BARBELL);
            case "primaryMuscles" -> row.setPrimaryMuscles(null);
            case "secondaryMuscles" -> row.setSecondaryMuscles(List.of("changed"));
            case "instructions" -> row.setInstructions(List.of("two", "one"));
            case "category" -> row.setCategory(row.getCategory() == Category.CARDIO ? Category.STRENGTH : Category.CARDIO);
            case "membership" -> rows.clear();
            case "addition" -> rows.add(Exercise.builder().id(new UUID(0, 2)).name("Legacy addition").build());
            default -> throw new AssertionError(field);
        }
        JsonNode after = snapshot("");
        assertThat(after.path("catalogVersion").asText()).isNotEqualTo(before.path("catalogVersion").asText());
        assertThat(after.path("sourceDatasetVersion")).isEqualTo(before.path("sourceDatasetVersion"));
    }

    @Test
    void catalog_nullEmptyAndOrderedArrays_haveDistinctVersions() throws Exception {
        Exercise row = row();
        when(repository.findAll()).thenReturn(List.of(row));
        row.setInstructions(null);
        String nullVersion = snapshot("").path("catalogVersion").asText();
        row.setInstructions(List.of());
        String emptyVersion = snapshot("").path("catalogVersion").asText();
        row.setInstructions(List.of("one", "two"));
        String orderedVersion = snapshot("").path("catalogVersion").asText();
        row.setInstructions(List.of("two", "one"));
        assertThat(Set.of(nullVersion, emptyVersion, orderedVersion, snapshot("").path("catalogVersion").asText())).hasSize(4);
    }

    @Test
    void catalog_emptyDatabase_returnsEmptyPendingSnapshotWithoutImporting() throws Exception {
        when(repository.findAll()).thenReturn(List.of());

        JsonNode response = snapshot("");

        assertThat(response.path("items").isEmpty()).isTrue();
        assertThat(response.path("mappingComplete").asBoolean()).isFalse();
        assertThat(response.path("unresolvedSourceKeys").size()).isEqualTo(catalog.parse(catalog.pinnedJson()).size());
        assertThat(response.path("retiredSourceKeys").isEmpty()).isTrue();
        assertThat(response.path("unresolvedLegacyIds").isEmpty()).isTrue();
        verify(repository).findAll();
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(exerciseService);
    }

    @Test
    void search_existingJsonContract_keepsUuidAndHidesCatalogFields() throws Exception {
        Exercise row = row();
        row.setRetired(true);
        when(exerciseService.searchExercises("Bench", Category.STRENGTH, Level.BEGINNER,
                List.of(MuscleGroup.CHEST), List.of(MuscleGroup.TRICEPS), Equipment.BARBELL))
                .thenReturn(List.of(row));

        JsonNode response = mapper.readTree(mvc.perform(get("/api/exercises/search").with(jwt())
                        .param("name", "Bench").param("category", "STRENGTH").param("level", "BEGINNER")
                        .param("primaryMuscles", "CHEST").param("secondaryMuscles", "TRICEPS")
                        .param("equipment", "BARBELL"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(response.isArray()).isTrue();
        assertThat(response.size()).isEqualTo(1);
        JsonNode item = response.get(0);
        assertThat(item.path("id").asText()).isEqualTo(row.getId().toString());
        assertThat(item.path("name").asText()).isEqualTo(row.getName());
        assertThat(item.path("level").asText()).isEqualTo("BEGINNER");
        assertThat(item.path("instructions")).isEqualTo(mapper.valueToTree(row.getInstructions()));
        assertThat(item.has("sourceKey")).isFalse();
        assertThat(item.has("retired")).isFalse();
        assertThat(item.size()).isEqualTo(10);
        verify(exerciseService).searchExercises("Bench", Category.STRENGTH, Level.BEGINNER,
                List.of(MuscleGroup.CHEST), List.of(MuscleGroup.TRICEPS), Equipment.BARBELL);
        verifyNoMoreInteractions(exerciseService);
        verifyNoInteractions(repository);
    }

    private Exercise row() {
        return Exercise.builder().id(new UUID(0, 1)).sourceKey("Bench_Press").name("Bench Press")
                .level(Level.BEGINNER).force(ForceType.PUSH).mechanic(MechanicType.COMPOUND)
                .equipment(Equipment.BARBELL).primaryMuscles(List.of("chest"))
                .secondaryMuscles(List.of("triceps", "shoulders")).instructions(List.of("one", "two"))
                .category(Category.STRENGTH).build();
    }

    private JsonNode snapshot(String query) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/exercises/catalog" + query).with(jwt()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
