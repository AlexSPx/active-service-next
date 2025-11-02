package com.services.active.dto;

import com.services.active.models.types.StreakUpdateStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreakUpdateResponse {
    private StreakUpdateStatus status;
    private int currentStreak;
    private int longestStreak;
    private String nextWorkoutId;
    private LocalDate nextWorkoutDeadline;
    private int streakFreezeCount;
}

