package com.services.active.repository;

import com.services.active.models.ExerciseRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ExerciseRecordRepository extends MongoRepository<ExerciseRecord, String>, ExerciseRecordRepositoryCustom {
}
