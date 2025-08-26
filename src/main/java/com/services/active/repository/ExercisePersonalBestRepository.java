package com.services.active.repository;

import com.services.active.models.ExercisePersonalBest;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ExercisePersonalBestRepository extends MongoRepository<ExercisePersonalBest, String> {
    Optional<ExercisePersonalBest> findByUserIdAndExerciseId(String userId, String exerciseId);
}

