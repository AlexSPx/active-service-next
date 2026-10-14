package com.services.active.dto;

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
public class WorkoutDeleteAck {
    private UUID deletedWorkoutId;
    private List<EntityVersion> routineVersions;

    public record EntityVersion(UUID id, Long version) {}
}
