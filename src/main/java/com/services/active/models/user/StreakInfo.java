package com.services.active.models.user;

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
public class StreakInfo {
    private int currentStreak;
    private int longestStreak;
    private String nextWorkoutId;
    private LocalDate nextWorkoutDeadline;
    private int streakFreezeCount;
    private LocalDate lastWorkoutCountedDate;

    /**
     * For WEEKLY_COMPLETION routines: tracks which workout IDs have been completed in the current week.
     * Resets every Monday.
     */
    private Set<String> weeklyCompletedWorkoutIds;

    /**
     * For WEEKLY_COMPLETION routines: the Monday of the current tracking week.
     */
    private LocalDate currentWeekStart;
}
