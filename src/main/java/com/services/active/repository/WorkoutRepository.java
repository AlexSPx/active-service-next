package com.services.active.repository;

import com.services.active.models.Workout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkoutRepository extends JpaRepository<Workout, UUID> {
    List<Workout> findAllByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
