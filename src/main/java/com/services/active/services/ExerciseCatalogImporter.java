package com.services.active.services;

import com.services.active.models.Exercise;
import com.services.active.repository.ExerciseRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExerciseCatalogImporter {
    private final ExerciseRepository repository;
    private final ExerciseCatalog catalog;
    private final EntityManager entityManager;

    public enum Reason { AMBIGUOUS_FINGERPRINT, POSSIBLE_KEY_RENAME, LEGACY_METADATA_MISMATCH, UNMATCHED_LEGACY }
    public record Diagnostic(String sourceKey, Reason reason, List<UUID> candidateIds) {}
    public record ImportReport(int inserted, int bound, int updated, int retired, int reactivated,
                               List<Diagnostic> diagnostics) {}

    @Transactional
    public ImportReport importPinnedCatalog() {
        return synchronize(catalog.parse(catalog.pinnedJson()));
    }

    /** Complete authoritative snapshot: omitted mapped keys retire, and invalid input never mutates rows. */
    @Transactional
    public ImportReport importCatalog(String json) {
        return synchronize(catalog.parse(json));
    }

    private ImportReport synchronize(Map<String, Exercise> incoming) {
        // Transaction-scoped lock also serializes imports when the table is empty, across service instances.
        entityManager.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(714876001)").getSingleResult();
        List<Exercise> existing = repository.findAll();
        Map<String, Exercise> mapped = existing.stream().filter(e -> e.getSourceKey() != null)
                .collect(Collectors.toMap(Exercise::getSourceKey, e -> e));
        Map<String, List<Exercise>> byFingerprint = existing.stream()
                .collect(Collectors.groupingBy(catalog::fingerprint));
        Map<String, List<Exercise>> mappedByFingerprint = existing.stream().filter(e -> e.getSourceKey() != null)
                .collect(Collectors.groupingBy(catalog::fingerprint));
        Map<UUID, String> fingerprints = existing.stream()
                .collect(Collectors.toMap(Exercise::getId, catalog::fingerprint));
        List<Exercise> legacy = existing.stream().filter(e -> e.getSourceKey() == null).toList();
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<Exercise> writes = new ArrayList<>();
        Set<UUID> boundIds = new HashSet<>();
        int inserted = 0, bound = 0, updated = 0, retired = 0, reactivated = 0;
        for (var entry : incoming.entrySet()) {
            String key = entry.getKey();
            Exercise metadata = entry.getValue();
            Exercise row = mapped.get(key);
            if (row == null) {
                String reviewed = catalog.legacyFingerprint(key);
                String current = catalog.fingerprint(metadata);
                List<Exercise> exact = byFingerprint.getOrDefault(reviewed, List.of());
                Set<Exercise> mappedMatches = new LinkedHashSet<>(mappedByFingerprint.getOrDefault(current, List.of()));
                mappedMatches.addAll(mappedByFingerprint.getOrDefault(reviewed, List.of()));
                if (!mappedMatches.isEmpty()) {
                    diagnostics.add(diagnostic(key, Reason.POSSIBLE_KEY_RENAME, List.copyOf(mappedMatches)));
                    continue;
                }
                List<Exercise> candidates = exact.stream().filter(e -> e.getSourceKey() == null).toList();
                if (candidates.size() > 1 || candidates.stream().anyMatch(e -> boundIds.contains(e.getId()))) {
                    diagnostics.add(diagnostic(key, Reason.AMBIGUOUS_FINGERPRINT, candidates));
                    continue;
                }
                if (candidates.size() == 1) {
                    row = candidates.getFirst();
                    boundIds.add(row.getId());
                    row.setSourceKey(key);
                    bound++;
                } else {
                    List<Exercise> uncertain = legacy.stream().filter(e ->
                            Objects.equals(e.getName(), metadata.getName()) ||
                            Objects.equals(e.getName(), catalog.legacyName(key)) ||
                            current.equals(fingerprints.get(e.getId()))).toList();
                    if (!uncertain.isEmpty()) {
                        diagnostics.add(diagnostic(key, Reason.LEGACY_METADATA_MISMATCH, uncertain));
                        continue;
                    }
                    row = Exercise.builder().id(UUID.randomUUID()).sourceKey(key).build();
                    inserted++;
                }
            } else if (!catalog.fingerprint(row).equals(catalog.fingerprint(metadata))) {
                updated++;
            }
            if (row.isRetired()) reactivated++;
            copyMetadata(metadata, row);
            row.setRetired(false);
            writes.add(row);
        }
        for (Exercise row : existing) {
            if (row.getSourceKey() != null && !incoming.containsKey(row.getSourceKey()) && !row.isRetired()) {
                row.setRetired(true);
                writes.add(row);
                retired++;
            }
        }
        for (Exercise row : legacy) {
            if (!boundIds.contains(row.getId())) {
                diagnostics.add(diagnostic(null, Reason.UNMATCHED_LEGACY, List.of(row)));
            }
        }
        repository.saveAll(writes);
        repository.flush();
        return new ImportReport(inserted, bound, updated, retired, reactivated, List.copyOf(diagnostics));
    }

    private static Diagnostic diagnostic(String key, Reason reason, List<Exercise> rows) {
        return new Diagnostic(key, reason, rows.stream().map(Exercise::getId).sorted().toList());
    }

    private static void copyMetadata(Exercise from, Exercise to) {
        to.setName(from.getName());
        to.setLevel(from.getLevel());
        to.setForce(from.getForce());
        to.setMechanic(from.getMechanic());
        to.setEquipment(from.getEquipment());
        to.setCategory(from.getCategory());
        to.setPrimaryMuscles(from.getPrimaryMuscles());
        to.setSecondaryMuscles(from.getSecondaryMuscles());
        to.setInstructions(from.getInstructions());
    }
}
