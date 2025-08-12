package com.services.active.dto;

import com.services.active.models.Workout;
import com.services.active.models.WorkoutTemplate;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WorkoutWithTemplate {
    private String id;
    private String title;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private WorkoutTemplate workoutTemplate;

    public static WorkoutWithTemplate createFromWorkoutAndTemplate(Workout workout, WorkoutTemplate template) {
        return WorkoutWithTemplate.builder()
                .id(workout.getId())
                .title(workout.getTitle())
                .notes(workout.getNotes())
                .createdAt(workout.getCreatedAt())
                .updatedAt(workout.getUpdatedAt())
                .workoutTemplate(template)
                .build();
    }
}
