package com.services.active.repository;

import com.services.active.models.WorkoutTemplate;
import lombok.NonNull;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface WorkoutTemplateRepository extends ReactiveMongoRepository<WorkoutTemplate, String> {
    Mono<WorkoutTemplate> findById(@NonNull String id);
}
