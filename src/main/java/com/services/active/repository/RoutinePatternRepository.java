package com.services.active.repository;

import com.services.active.models.RoutinePattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RoutinePatternRepository extends JpaRepository<RoutinePattern, UUID> {
    List<RoutinePattern> findAllByRoutineIdOrderByDayIndexAsc(UUID routineId);
    void deleteByRoutineId(UUID routineId);

    @Query("SELECT DISTINCT p.routineId FROM RoutinePattern p WHERE p.workoutId = :workoutId ORDER BY p.routineId")
    List<UUID> findRoutineIdsByWorkoutId(@Param("workoutId") UUID workoutId);
}
