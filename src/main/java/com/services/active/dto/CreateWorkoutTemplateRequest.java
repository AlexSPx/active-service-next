package com.services.active.dto;

import com.services.active.models.TemplateExercise;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for creating a new workout template")
public class CreateWorkoutTemplateRequest {
    @Schema(description = "List of exercises in the template")
    private List<TemplateExercise> exercises;
}
