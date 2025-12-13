package com.services.active.dto;

import com.services.active.models.RoutinePattern;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Request payload for updating an existing routine")
public class UpdateRoutineRequest {
    @Schema(description = "Routine name", example = "Push/Pull/Legs")
    private String name;

    @Schema(description = "Routine description", example = "6-day split with rest on Sunday")
    private String description;

    @Schema(description = "Updated pattern for the routine; if null, pattern is not changed")
    private List<RoutinePattern> pattern;

    @Schema(description = "Whether this routine should be active; if true, other routines will be deactivated")
    private Boolean active;

    @Schema(description = "Start date for the routine; if null, not changed", example = "2025-12-11")
    private LocalDate startDate;
}
