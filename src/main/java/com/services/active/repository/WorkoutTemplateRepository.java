package com.services.active.repository;

import com.services.active.models.WorkoutTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WorkoutTemplateRepository extends JpaRepository<WorkoutTemplate, UUID> {
}
