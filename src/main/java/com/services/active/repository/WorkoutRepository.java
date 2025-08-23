package com.services.active.repository;

import com.services.active.models.Workout;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface WorkoutRepository extends MongoRepository<Workout, String>, WorkoutRepositoryCustom {
    List<Workout> findAllByUserId(String userId);

    Workout getWorkoutById(String id);
}
