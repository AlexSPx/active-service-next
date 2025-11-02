package com.services.active.dto;

import com.services.active.models.RoutinePattern;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @NotNull(message = "Pattern is required")
    @Schema(description = "Pattern for the routine")
    private List<RoutinePattern> pattern;

    @Schema(description = "Whether this routine should be active")
    private Boolean active;
}
