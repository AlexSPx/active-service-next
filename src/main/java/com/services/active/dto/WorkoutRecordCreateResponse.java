package com.services.active.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutRecordCreateResponse {
    private UserWorkoutRecordsResponse workoutRecord;
    private StreakUpdateResponse streakUpdate;
}

