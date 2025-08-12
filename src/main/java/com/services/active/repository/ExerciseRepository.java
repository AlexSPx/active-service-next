package com.services.active.repository;

import com.services.active.models.Exercise;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExerciseRepository extends ReactiveMongoRepository<Exercise, String> {
}
