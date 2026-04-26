package com.services.active.services;

import com.services.active.dto.ExerciseLogResponse;
import com.services.active.exceptions.NotFoundException;
import com.services.active.models.Exercise;
import com.services.active.models.ExerciseRecord;
import com.services.active.models.user.User;
import com.services.active.models.types.Category;
import com.services.active.models.types.Equipment;
import com.services.active.models.types.Level;
import com.services.active.models.types.MuscleGroup;
import com.services.active.repository.ExerciseRecordRepository;
import com.services.active.repository.ExerciseRepository;
import com.services.active.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExerciseService {
    private final ExerciseRepository exerciseRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final UserRepository userRepository;

    public List<Exercise> searchExercises(String name, Category category, Level level,
                                          List<MuscleGroup> primaryMuscles, List<MuscleGroup> secondaryMuscles,
                                          Equipment equipment) {
        Specification<Exercise> spec = (root, query, cb) -> null;

        if (name != null && !name.trim().isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
        }
        if (category != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("category"), category));
        }
        if (level != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("level"), level));
        }
        if (equipment != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("equipment"), equipment));
        }
        // Note: Filtering by array-contains for primaryMuscles/secondaryMuscles
        // requires native queries with PostgreSQL array operators.
        // For now, we filter in memory for the muscle group search.

        List<Exercise> results = exerciseRepository.findAll(spec);

        // In-memory filter for muscle groups (PostgreSQL array contains)
        if (primaryMuscles != null && !primaryMuscles.isEmpty()) {
            List<String> targetMuscles = primaryMuscles.stream()
                    .map(MuscleGroup::getName)
                    .collect(Collectors.toList());
            results = results.stream()
                    .filter(e -> e.getPrimaryMuscles() != null && e.getPrimaryMuscles().containsAll(targetMuscles))
                    .collect(Collectors.toList());
        }
        if (secondaryMuscles != null && !secondaryMuscles.isEmpty()) {
            List<String> targetMuscles = secondaryMuscles.stream()
                    .map(MuscleGroup::getName)
                    .collect(Collectors.toList());
            results = results.stream()
                    .filter(e -> e.getSecondaryMuscles() != null && e.getSecondaryMuscles().containsAll(targetMuscles))
                    .collect(Collectors.toList());
        }

        return results;
    }

    public List<ExerciseLogResponse> getExerciseLogs(String workosId, String exerciseId) {
        UUID exId = UUID.fromString(exerciseId);
        Exercise exercise = exerciseRepository.findById(exId)
                .orElseThrow(() -> new NotFoundException("Exercise not found: " + exerciseId));

        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        List<ExerciseRecord> exerciseRecords = exerciseRecordRepository
                .findByUserIdAndExerciseIdOrderByCreatedAtAsc(user.getId(), exId);

        if (exerciseRecords.isEmpty()) {
            return new ArrayList<>();
        }

        return exerciseRecords.stream()
                .map(record -> ExerciseLogResponse.builder()
                        .exerciseRecordId(record.getId().toString())
                        .exerciseId(record.getExerciseId().toString())
                        .exerciseName(exercise.getName())
                        .createdAt(record.getCreatedAt())
                        .reps(record.getReps())
                        .weight(record.getWeight())
                        .durationSeconds(record.getDurationSeconds())
                        .notes(record.getNotes())
                        .achievedOneRmValue(record.getAchievedOneRmValue())
                        .achievedOneRmSetIndex(record.getAchievedOneRmSetIndex())
                        .achievedTotalVolumeValue(record.getAchievedTotalVolumeValue())
                        .build())
                .collect(Collectors.toList());
    }
}
