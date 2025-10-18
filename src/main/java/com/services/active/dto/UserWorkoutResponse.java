package com.services.active.dto;

import com.services.active.models.Workout;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserWorkoutResponse {
    private String id;
    private String title;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private WorkoutTemplateResponse workoutTemplate;

    public static UserWorkoutResponse from(Workout workout, WorkoutTemplateResponse template) {
        return UserWorkoutResponse.builder()
                .id(workout.getId())
                .title(workout.getTitle())
                .notes(workout.getNotes())
                .createdAt(workout.getCreatedAt())
                .updatedAt(workout.getUpdatedAt())
                .workoutTemplate(template)
                .build();
    }
}

