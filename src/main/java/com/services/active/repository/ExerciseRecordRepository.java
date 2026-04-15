package com.services.active.repository;

import com.services.active.models.ExerciseRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExerciseRecordRepository extends JpaRepository<ExerciseRecord, UUID> {
    List<ExerciseRecord> findByUserIdAndExerciseIdOrderByCreatedAtAsc(UUID userId, UUID exerciseId);
    List<ExerciseRecord> findByUserIdOrderByCreatedAtAsc(UUID userId);
    List<ExerciseRecord> findAllByWorkoutRecordIdOrderByOrdinalAsc(UUID workoutRecordId);
    void deleteByUserId(UUID userId);
}
