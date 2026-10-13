package com.services.active.repository;

import com.services.active.models.Workout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkoutRepository extends JpaRepository<Workout, UUID> {
    List<Workout> findAllByUserId(UUID userId);
    Optional<Workout> findByIdAndUserId(UUID id, UUID userId);
    List<Workout> findAllByIdInAndUserId(Collection<UUID> ids, UUID userId);
    void deleteByUserId(UUID userId);
}
