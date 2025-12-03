package com.services.active.repository;

import com.services.active.models.WorkoutRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface WorkoutRecordRepository extends MongoRepository<WorkoutRecord, String> {
    List<WorkoutRecord> findAllByUserId(String userId);
    void deleteByUserId(String userId);
}
