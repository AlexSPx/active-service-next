package com.services.active.repository;

import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import com.services.active.models.Workout;

@RequiredArgsConstructor
public class WorkoutRepositoryImpl implements WorkoutRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public UpdateResult updateWorkoutRecordIds(String workoutId, String workoutRecordId) {
        Query query = new Query(Criteria.where("_id").is(workoutId));
        Update update = new Update().push("workoutRecordIds", workoutRecordId);
        return mongoTemplate.updateFirst(query, update, Workout.class);
    }
}
