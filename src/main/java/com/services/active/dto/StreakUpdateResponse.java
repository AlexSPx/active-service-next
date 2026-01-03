package com.services.active.dto;

import com.services.active.models.types.StreakUpdateStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Set;

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

    /**
     * For WEEKLY_COMPLETION routines: workout IDs completed this week.
     */
    private Set<String> weeklyCompletedWorkoutIds;

    /**
     * For WEEKLY_COMPLETION routines: total workouts required this week.
     */
    private Integer weeklyWorkoutsRequired;
}

