package com.services.active.dto;

import com.services.active.models.RoutinePattern;
import com.services.active.models.types.RoutineType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for creating a new routine")
public class CreateRoutineRequest {
    @NotBlank(message = "Name is required")
    @Schema(description = "Routine name", example = "Push/Pull/Legs")
    private String name;

    @Schema(description = "Routine description", example = "6-day split with rest on Sunday")
    private String description;

    @NotEmpty(message = "Pattern is required")
    @Schema(description = "Pattern for the routine")
    private List<RoutinePattern> pattern;

    @Schema(description = "Whether this routine should be active")
    private Boolean active;

    @Schema(description = "Start date for the routine; defaults to creation day if omitted", example = "2025-12-11")
    private LocalDate startDate;

    @Schema(description = "Type of routine scheduling. SEQUENTIAL: workouts on specific days. WEEKLY_COMPLETION: complete all workouts within a week (Mon-Sun) in any order. Defaults to SEQUENTIAL.",
            example = "WEEKLY_COMPLETION")
    private RoutineType routineType;
}
