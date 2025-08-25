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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class WorkoutRecordBackfillRunner implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;
    private final WorkoutRepository workoutRepository;
    private final WorkoutRecordRepository workoutRecordRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // Find records missing workoutTitle (null, empty, or field absent)
            Query missingTitleQuery = new Query(new Criteria().orOperator(
                    Criteria.where("workoutTitle").exists(false),
                    Criteria.where("workoutTitle").is(null),
                    Criteria.where("workoutTitle").is("")
            ));
            List<WorkoutRecord> missingTitle = mongoTemplate.find(missingTitleQuery, WorkoutRecord.class);

            // Find records missing startTime (null or field absent)
            Query missingStartQuery = new Query(new Criteria().orOperator(
                    Criteria.where("startTime").exists(false),
                    Criteria.where("startTime").is(null)
            ));
            List<WorkoutRecord> missingStart = mongoTemplate.find(missingStartQuery, WorkoutRecord.class);

            // Merge to a unique map for single-pass updates
            Map<String, WorkoutRecord> toUpdate = new LinkedHashMap<>();
            for (WorkoutRecord r : missingTitle) toUpdate.put(r.getId(), r);
            for (WorkoutRecord r : missingStart) toUpdate.put(r.getId(), r);

            int updated = 0;
            for (WorkoutRecord record : toUpdate.values()) {
                boolean changed = false;

                // Backfill workoutTitle from the associated Workout
                if (record.getWorkoutTitle() == null || record.getWorkoutTitle().isBlank()) {
                    String title = null;
                    if (record.getWorkoutId() != null) {
                        Optional<Workout> workoutOpt = workoutRepository.findById(record.getWorkoutId());
                        title = workoutOpt.map(Workout::getTitle).orElse(null);
                    }
                    // If workout is missing, fall back to "Unknown"
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
                log.info("WorkoutRecordBackfillRunner: updated {} workout_record documents with missing fields", updated);
            } else {
                log.info("WorkoutRecordBackfillRunner: no workout_record documents required updates");
            }
        } catch (Exception e) {
            // Don't fail app startup because of backfill issues; just log and continue
            log.error("WorkoutRecordBackfillRunner failed: {}", e.getMessage(), e);
        }
    }
}

