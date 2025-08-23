package com.services.active.repository;

import com.mongodb.client.result.UpdateResult;

public interface WorkoutRepositoryCustom {
    UpdateResult updateWorkoutRecordIds(String workoutId, String workoutRecordId);
}
