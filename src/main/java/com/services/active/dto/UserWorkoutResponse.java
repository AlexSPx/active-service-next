package com.services.active.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long version;

    public static UserWorkoutResponse from(Workout workout, WorkoutTemplateResponse template) {
        return UserWorkoutResponse.builder()
                .id(workout.getId().toString())
                .title(workout.getTitle())
                .notes(workout.getNotes())
                .createdAt(workout.getCreatedAt())
                .updatedAt(workout.getUpdatedAt())
                .workoutTemplate(template)
                .version(workout.getVersion())
                .build();
    }
}

