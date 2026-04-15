package com.services.active.repository;

import com.services.active.models.user.UserWeeklyCompletedWorkout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserWeeklyCompletedWorkoutRepository extends JpaRepository<UserWeeklyCompletedWorkout, UUID> {
    List<UserWeeklyCompletedWorkout> findAllByUserId(UUID userId);
    boolean existsByUserIdAndWorkoutId(UUID userId, UUID workoutId);
    void deleteByUserId(UUID userId);
}
