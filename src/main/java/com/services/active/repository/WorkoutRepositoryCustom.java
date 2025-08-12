package com.services.active.repository;

import com.mongodb.client.result.UpdateResult;
import reactor.core.publisher.Mono;

public interface WorkoutRepositoryCustom {
    Mono<UpdateResult> updateWorkoutRecordIds(String workoutId, String workoutRecordId);
}

