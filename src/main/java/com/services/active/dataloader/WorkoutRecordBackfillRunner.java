package com.services.active.dataloader;

import com.services.active.models.Workout;
import com.services.active.models.WorkoutRecord;
import com.services.active.repository.WorkoutRecordRepository;
import com.services.active.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class WorkoutRecordBackfillRunner implements ApplicationRunner {

    private final WorkoutRepository workoutRepository;
    private final WorkoutRecordRepository workoutRecordRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // Find records missing workoutTitle or startTime
            List<WorkoutRecord> allRecords = workoutRecordRepository.findAll();
            int updated = 0;

            for (WorkoutRecord record : allRecords) {
                boolean changed = false;

                // Backfill workoutTitle from the associated Workout
                if (record.getWorkoutTitle() == null || record.getWorkoutTitle().isBlank()) {
                    String title = null;
                    if (record.getWorkoutId() != null) {
                        Optional<Workout> workoutOpt = workoutRepository.findById(record.getWorkoutId());
                        title = workoutOpt.map(Workout::getTitle).orElse(null);
                    }
                    if (title == null || title.isBlank()) {
                        title = "Unknown";
                    }
                    record.setWorkoutTitle(title);
                    changed = true;
                }

                // Backfill startTime: 1 hour before createdAt
                if (record.getStartTime() == null) {
                    LocalDateTime base = record.getCreatedAt() != null
                            ? record.getCreatedAt()
                            : LocalDateTime.now();
                    record.setStartTime(base.minusHours(1));
                    changed = true;
                }

                if (changed) {
                    workoutRecordRepository.save(record);
                    updated++;
                }
            }

            if (updated > 0) {
                log.info("WorkoutRecordBackfillRunner: updated {} workout_record rows with missing fields", updated);
            } else {
                log.info("WorkoutRecordBackfillRunner: no workout_record rows required updates");
            }
        } catch (Exception e) {
            log.error("WorkoutRecordBackfillRunner failed: {}", e.getMessage(), e);
        }
    }
}
