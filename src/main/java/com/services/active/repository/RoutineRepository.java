package com.services.active.repository;

import com.services.active.models.Routine;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoutineRepository extends MongoRepository<Routine, String> {
    List<Routine> findAllByUserIdOrderByCreatedAtDesc(String userId);
    Optional<Routine> findByIdAndUserId(String id, String userId);
    boolean existsByUserIdAndNameIgnoreCase(String userId, String name);
}
