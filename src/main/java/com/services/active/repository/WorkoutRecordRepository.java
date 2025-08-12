package com.services.active.repository;

import com.services.active.models.WorkoutRecord;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface WorkoutRecordRepository extends ReactiveMongoRepository<WorkoutRecord, String> {
    Flux<WorkoutRecord> findAllByUserId(String userId);
}
