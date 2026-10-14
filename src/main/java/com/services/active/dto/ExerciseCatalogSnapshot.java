package com.services.active.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.services.active.models.Exercise;

import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ExerciseCatalogSnapshot(
        String catalogVersion,
        String sourceDatasetVersion,
        boolean mappingComplete,
        List<String> unresolvedSourceKeys,
        List<String> retiredSourceKeys,
        List<UUID> unresolvedLegacyIds,
        List<Item> items) {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonPropertyOrder({"id", "sourceKey", "retired", "name", "level", "force", "mechanic", "equipment",
            "primaryMuscles", "secondaryMuscles", "instructions", "category"})
    public record Item(UUID id, String sourceKey, boolean retired, String name, String level, String force,
                       String mechanic, String equipment, List<String> primaryMuscles, List<String> secondaryMuscles,
                       List<String> instructions, String category) {
        public static Item from(Exercise exercise) {
            return new Item(exercise.getId(), exercise.getSourceKey(), exercise.isRetired(), exercise.getName(),
                    constant(exercise.getLevel()), constant(exercise.getForce()), constant(exercise.getMechanic()),
                    constant(exercise.getEquipment()), copy(exercise.getPrimaryMuscles()),
                    copy(exercise.getSecondaryMuscles()), copy(exercise.getInstructions()), constant(exercise.getCategory()));
        }

        private static String constant(Enum<?> value) { return value == null ? null : value.name(); }
        private static List<String> copy(List<String> values) {
            return values == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(values));
        }
    }
}
