package com.services.active.models.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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
}
