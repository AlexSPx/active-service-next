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
@Schema(description = "Request payload for updating an existing workout")
public class UpdateWorkoutRequest {
    @Schema(description = "Name of the workout", example = "Push Day Workout")
    private String title;

    @Schema(description = "Additional notes about the workout", example = "Focus on form and progressive overload")
    private String notes;

    @Schema(description = "Updated template to apply to the workout; if exercises are empty or null, the template is not changed")
    private CreateWorkoutTemplateRequest template;
}
