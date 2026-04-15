package com.services.active.repository;

import com.services.active.models.RoutinePattern;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoutinePatternRepository extends JpaRepository<RoutinePattern, UUID> {
    List<RoutinePattern> findAllByRoutineIdOrderByDayIndexAsc(UUID routineId);
    void deleteByRoutineId(UUID routineId);
}
