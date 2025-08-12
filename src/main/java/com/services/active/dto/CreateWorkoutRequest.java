package com.services.active.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for creating a new workout")
public class CreateWorkoutRequest {
    @Schema(description = "Name of the workout", example = "Push Day Workout", required = true)
    private String title;

    @Schema(description = "Additional notes about the workout", example = "Focus on form and progressive overload")
    private String notes;

    @Schema(description = "New template to create alongside the workout")
    private CreateWorkoutTemplateRequest template;
}
