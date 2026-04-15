package com.services.active.repository;

import com.services.active.models.TemplateExercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TemplateExerciseRepository extends JpaRepository<TemplateExercise, UUID> {
    List<TemplateExercise> findAllByTemplateIdOrderByOrdinalAsc(UUID templateId);
    void deleteByTemplateId(UUID templateId);
}
