package com.services.active.repository;

import com.services.active.models.ExercisePersonalBest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExercisePersonalBestRepository extends JpaRepository<ExercisePersonalBest, UUID> {
    Optional<ExercisePersonalBest> findByUserIdAndExerciseId(UUID userId, UUID exerciseId);
    void deleteByUserId(UUID userId);
    List<ExercisePersonalBest> findAllByUserId(UUID userId);
}
