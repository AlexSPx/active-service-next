package com.services.active.repository;

import com.services.active.models.WorkoutTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkoutTemplateRepository extends MongoRepository<WorkoutTemplate, String> {
}
