package com.services.active.repository;

import com.services.active.models.Workout;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface WorkoutRepository extends ReactiveMongoRepository<Workout, String>, WorkoutRepositoryCustom {
    Flux<Workout> findAllByUserId(String userId);
}
