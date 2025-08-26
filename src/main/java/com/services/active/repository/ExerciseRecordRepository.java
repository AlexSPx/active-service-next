package com.services.active.repository;

import com.services.active.models.ExerciseRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ExerciseRecordRepository extends MongoRepository<ExerciseRecord, String>, ExerciseRecordRepositoryCustom {
    List<ExerciseRecord> findByUserIdAndExerciseIdOrderByCreatedAtAsc(String userId, String exerciseId);
    List<ExerciseRecord> findByUserIdOrderByCreatedAtAsc(String userId);
}
