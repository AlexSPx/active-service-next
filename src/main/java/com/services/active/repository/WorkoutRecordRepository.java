package com.services.active.repository;

import com.services.active.models.WorkoutRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkoutRecordRepository extends JpaRepository<WorkoutRecord, UUID> {
    List<WorkoutRecord> findAllByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
