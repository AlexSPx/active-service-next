package com.services.active.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for creating a new workout template")
public class CreateWorkoutTemplateRequest {
    @Schema(description = "List of exercises in the template")
    private List<TemplateExerciseRequest> exercises;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateExerciseRequest {
        private UUID exerciseId;
        private List<Integer> reps;
        private List<Double> weight;
        private List<Integer> durationSeconds;
        private String notes;
    }
}
