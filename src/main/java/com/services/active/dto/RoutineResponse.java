package com.services.active.dto;

import com.services.active.models.Routine;
import com.services.active.models.RoutinePattern;
import com.services.active.models.types.RoutineType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutineResponse {
    private UUID id;
    private String name;
    private String description;
    private UUID userId;
    private RoutineType routineType;
    private Instant startDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<RoutinePattern> pattern;

    public static RoutineResponse from(Routine routine, List<RoutinePattern> patterns) {
        return RoutineResponse.builder()
                .id(routine.getId())
                .name(routine.getName())
                .description(routine.getDescription())
                .userId(routine.getUserId())
                .routineType(routine.getRoutineType())
                .startDate(routine.getStartDate())
                .createdAt(routine.getCreatedAt())
                .updatedAt(routine.getUpdatedAt())
                .pattern(patterns)
                .build();
    }
}
