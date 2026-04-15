package com.services.active.dto;

import com.services.active.models.types.DayType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutinePatternRequest {
    private int dayIndex;
    private DayType dayType;
    private UUID workoutId;
}
