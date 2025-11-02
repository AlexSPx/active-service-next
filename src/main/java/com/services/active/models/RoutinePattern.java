package com.services.active.models;

import com.services.active.models.types.DayType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutinePattern {
    private int dayIndex;
    private DayType dayType;
    private String workoutId;
}
