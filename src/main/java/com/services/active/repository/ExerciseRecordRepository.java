package com.services.active.repository;

import com.services.active.models.ExerciseRecord;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface ExerciseRecordRepository extends ReactiveMongoRepository<ExerciseRecord, String>, ExerciseRecordRepositoryCustom {
}
