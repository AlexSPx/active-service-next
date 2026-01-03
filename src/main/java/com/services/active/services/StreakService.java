package com.services.active.services;

import com.services.active.exceptions.NotFoundException;
import com.services.active.models.Routine;
import com.services.active.models.RoutinePattern;
import com.services.active.models.user.StreakInfo;
import com.services.active.models.user.User;
import com.services.active.models.types.DayType;
import com.services.active.models.types.RoutineType;
import com.services.active.models.types.StreakUpdateStatus;
import com.services.active.dto.StreakUpdateResponse;
import com.services.active.repository.RoutineRepository;
import com.services.active.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StreakService {

    private final RoutineRepository routineRepository;
    private final UserRepository userRepository;

    public void checkStreak(User user) {
        StreakInfo streak = safeStreak(user);
        LocalDate deadline = streak.getNextWorkoutDeadline();
        if (deadline == null) return;

        LocalDate today = LocalDate.now();
        if (!today.isAfter(deadline)) return;

        // Check if this is a WEEKLY_COMPLETION routine
        Optional<Routine> activeRoutine = getActiveRoutine(user);
        if (activeRoutine.isPresent() && activeRoutine.get().getRoutineType() == RoutineType.WEEKLY_COMPLETION) {
            // For weekly routines, reset the weekly tracking and handle streak
            if (streak.getStreakFreezeCount() > 0) {
                streak.setStreakFreezeCount(streak.getStreakFreezeCount() - 1);
            } else {
                streak.setCurrentStreak(0);
            }
            // Reset weekly tracking for the new week
            LocalDate currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            streak.setCurrentWeekStart(currentMonday);
            streak.setWeeklyCompletedWorkoutIds(new HashSet<>());
            streak.setNextWorkoutDeadline(currentMonday.plusDays(6)); // End of current week
            streak.setNextWorkoutId(null);
        } else {
            // SEQUENTIAL routine logic
            if (streak.getStreakFreezeCount() > 0) {
                streak.setStreakFreezeCount(streak.getStreakFreezeCount() - 1);
                NextWorkout next = calculateNextWorkoutDay(user, deadline);
                streak.setNextWorkoutId(next.workoutId());
                streak.setNextWorkoutDeadline(next.deadline());
            } else {
                streak.setCurrentStreak(0);
                streak.setNextWorkoutId(null);
                streak.setNextWorkoutDeadline(null);
            }
        }
        userRepository.save(user);
    }

    public StreakUpdateResponse onWorkoutCompleted(String workosId, String completedWorkoutId) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        StreakInfo streak = safeStreak(user);
        LocalDate today = LocalDate.now();

        // Check if we should use weekly completion logic
        Optional<Routine> activeRoutine = getActiveRoutine(user);
        if (activeRoutine.isPresent() && activeRoutine.get().getRoutineType() == RoutineType.WEEKLY_COMPLETION) {
            return onWeeklyWorkoutCompleted(user, streak, activeRoutine.get(), completedWorkoutId, today);
        }

        // SEQUENTIAL routine logic (original behavior)
        return onSequentialWorkoutCompleted(user, streak, completedWorkoutId, today);
    }

    private StreakUpdateResponse onSequentialWorkoutCompleted(User user, StreakInfo streak, String completedWorkoutId, LocalDate today) {
        // Block multiple streak counts within the same calendar day
        if (streak.getLastWorkoutCountedDate() != null && today.isEqual(streak.getLastWorkoutCountedDate())) {
            return snapshot(streak, StreakUpdateStatus.WRONG_WORKOUT);
        }

        String expectedWorkoutId = streak.getNextWorkoutId();

        if (expectedWorkoutId != null && !expectedWorkoutId.equals(completedWorkoutId)) {
            // Wrong workout for the current deadline window; do nothing
            return snapshot(streak, StreakUpdateStatus.WRONG_WORKOUT);
        }

        LocalDate deadline = streak.getNextWorkoutDeadline();
        StreakUpdateStatus status;
        if (deadline == null) {
            // Starting a new streak
            streak.setCurrentStreak(1);
            if (streak.getCurrentStreak() > streak.getLongestStreak()) {
                streak.setLongestStreak(streak.getCurrentStreak());
            }
            NextWorkout next = calculateNextWorkoutDay(user, today);
            streak.setNextWorkoutId(next.workoutId());
            streak.setNextWorkoutDeadline(next.deadline());
            streak.setLastWorkoutCountedDate(today);
            userRepository.save(user);
            status = StreakUpdateStatus.STARTED;
            return snapshot(streak, status);
        }

        if (today.isAfter(deadline)) {
            // Late: reset to 1 for this workout
            streak.setCurrentStreak(1);
            if (streak.getCurrentStreak() > streak.getLongestStreak()) {
                streak.setLongestStreak(streak.getCurrentStreak());
            }
            NextWorkout next = calculateNextWorkoutDay(user, today);
            streak.setNextWorkoutId(next.workoutId());
            streak.setNextWorkoutDeadline(next.deadline());
            streak.setLastWorkoutCountedDate(today);
            userRepository.save(user);
            status = StreakUpdateStatus.BROKEN_RESET;
        } else {
            int prev = streak.getCurrentStreak();
            streak.setCurrentStreak(prev + 1);
            if (streak.getCurrentStreak() > streak.getLongestStreak()) {
                streak.setLongestStreak(streak.getCurrentStreak());
            }
            NextWorkout next = calculateNextWorkoutDay(user, today);
            streak.setNextWorkoutId(next.workoutId());
            streak.setNextWorkoutDeadline(next.deadline());
            streak.setLastWorkoutCountedDate(today);
            userRepository.save(user);
            status = (prev == 0) ? StreakUpdateStatus.STARTED : StreakUpdateStatus.CONTINUED;
        }

        return snapshot(streak, status);
    }

    private StreakUpdateResponse onWeeklyWorkoutCompleted(User user, StreakInfo streak, Routine routine,
                                                          String completedWorkoutId, LocalDate today) {
        // Get the Monday of the current week
        LocalDate currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = currentMonday.plusDays(6); // Sunday

        // Check if we need to reset weekly tracking (new week started)
        if (streak.getCurrentWeekStart() == null || !streak.getCurrentWeekStart().equals(currentMonday)) {
            // Check if previous week was missed (streak broken)
            if (streak.getCurrentWeekStart() != null) {
                LocalDate previousWeekMonday = streak.getCurrentWeekStart();
                LocalDate expectedNextMonday = previousWeekMonday.plusWeeks(1);

                // If current week is more than 1 week after the tracked week, we missed at least one week
                if (currentMonday.isAfter(expectedNextMonday)) {
                    // Missed at least one week - check if freeze available
                    if (streak.getStreakFreezeCount() > 0) {
                        streak.setStreakFreezeCount(streak.getStreakFreezeCount() - 1);
                    } else {
                        // Reset streak
                        streak.setCurrentStreak(0);
                    }
                }
            }
            // Start tracking new week
            streak.setCurrentWeekStart(currentMonday);
            streak.setWeeklyCompletedWorkoutIds(new HashSet<>());
        }

        // Get all required workout IDs from the routine pattern
        Set<String> requiredWorkoutIds = routine.getPattern().stream()
                .filter(p -> p.getDayType() == DayType.WORKOUT)
                .map(RoutinePattern::getWorkoutId)
                .collect(Collectors.toSet());
        int requiredCount = requiredWorkoutIds.size();

        // Check if the completed workout is part of this routine
        if (!requiredWorkoutIds.contains(completedWorkoutId)) {
            return snapshot(streak, StreakUpdateStatus.WRONG_WORKOUT, requiredCount);
        }

        // Check if this workout was already completed this week
        if (streak.getWeeklyCompletedWorkoutIds() == null) {
            streak.setWeeklyCompletedWorkoutIds(new HashSet<>());
        }

        if (streak.getWeeklyCompletedWorkoutIds().contains(completedWorkoutId)) {
            // Already completed this workout this week - no streak update
            return snapshot(streak, StreakUpdateStatus.WRONG_WORKOUT, requiredCount);
        }

        // Mark this workout as completed
        streak.getWeeklyCompletedWorkoutIds().add(completedWorkoutId);
        streak.setLastWorkoutCountedDate(today);

        // Check if all workouts for the week are now complete
        if (streak.getWeeklyCompletedWorkoutIds().containsAll(requiredWorkoutIds)) {
            // All workouts completed! Increment streak
            int prev = streak.getCurrentStreak();
            streak.setCurrentStreak(prev + 1);
            if (streak.getCurrentStreak() > streak.getLongestStreak()) {
                streak.setLongestStreak(streak.getCurrentStreak());
            }
            // Set deadline to end of next week
            LocalDate nextMonday = currentMonday.plusWeeks(1);
            streak.setNextWorkoutDeadline(nextMonday.plusDays(6)); // Next Sunday
            streak.setNextWorkoutId(null); // No specific workout required next

            userRepository.save(user);
            return snapshot(streak, prev == 0 ? StreakUpdateStatus.STARTED : StreakUpdateStatus.CONTINUED, requiredCount);
        } else {
            // Still workouts remaining this week
            // Find a remaining workout to set as next
            Set<String> remaining = new HashSet<>(requiredWorkoutIds);
            remaining.removeAll(streak.getWeeklyCompletedWorkoutIds());
            streak.setNextWorkoutId(remaining.iterator().next());
            streak.setNextWorkoutDeadline(endOfWeek);

            userRepository.save(user);
            return snapshot(streak, StreakUpdateStatus.WEEKLY_PROGRESS, requiredCount);
        }
    }

    private Optional<Routine> getActiveRoutine(User user) {
        String activeId = user.getActiveRoutineId();
        if (activeId == null || activeId.isBlank()) {
            return Optional.empty();
        }
        return routineRepository.findByIdAndUserId(activeId, user.getId());
    }

    private StreakInfo safeStreak(User user) {
        if (user.getStreak() == null) {
            user.setStreak(new StreakInfo());
        }
        return user.getStreak();
    }

    private StreakUpdateResponse snapshot(StreakInfo s, StreakUpdateStatus status) {
        return StreakUpdateResponse.builder()
                .status(status)
                .currentStreak(s.getCurrentStreak())
                .longestStreak(s.getLongestStreak())
                .nextWorkoutId(s.getNextWorkoutId())
                .nextWorkoutDeadline(s.getNextWorkoutDeadline())
                .streakFreezeCount(s.getStreakFreezeCount())
                .weeklyCompletedWorkoutIds(s.getWeeklyCompletedWorkoutIds())
                .build();
    }

    private StreakUpdateResponse snapshot(StreakInfo s, StreakUpdateStatus status, int weeklyWorkoutsRequired) {
        return StreakUpdateResponse.builder()
                .status(status)
                .currentStreak(s.getCurrentStreak())
                .longestStreak(s.getLongestStreak())
                .nextWorkoutId(s.getNextWorkoutId())
                .nextWorkoutDeadline(s.getNextWorkoutDeadline())
                .streakFreezeCount(s.getStreakFreezeCount())
                .weeklyCompletedWorkoutIds(s.getWeeklyCompletedWorkoutIds())
                .weeklyWorkoutsRequired(weeklyWorkoutsRequired)
                .build();
    }

    private record NextWorkout(String workoutId, LocalDate deadline) {}

    private NextWorkout calculateNextWorkoutDay(User user, LocalDate fromDate) {
        String activeId = user.getActiveRoutineId();
        if (activeId == null || activeId.isBlank()) {
            return new NextWorkout(null, fromDate.plusDays(1));
        }
        Optional<Routine> optRoutine = routineRepository.findByIdAndUserId(activeId, user.getId());
        if (optRoutine.isEmpty()) {
            return new NextWorkout(null, fromDate.plusDays(1));
        }
        Routine activeRoutine = optRoutine.get();
        List<RoutinePattern> pattern = activeRoutine.getPattern();
        if (pattern == null || pattern.isEmpty()) {
            return new NextWorkout(null, fromDate.plusDays(1));
        }

        // Determine the start date for the pattern. Use createdAt's date as a proxy.
        LocalDate startDate = Optional.ofNullable(activeRoutine.getCreatedAt())
                .map(LocalDateTime::toLocalDate)
                .orElse(fromDate);

        // Normalize pattern by dayIndex for quick lookups
        int patternLength = pattern.stream()
                .map(RoutinePattern::getDayIndex)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
        if (patternLength <= 0) {
            return new NextWorkout(null, fromDate.plusDays(1));
        }

        long daysSinceStart = ChronoUnit.DAYS.between(startDate, fromDate);
        int fromIndex = (int) Math.floorMod(daysSinceStart, patternLength);

        for (int i = 1; i <= patternLength; i++) {
            int nextIndex = Math.floorMod(fromIndex + i, patternLength);
            RoutinePattern nextPattern = pattern.stream()
                    .filter(p -> p.getDayIndex() == nextIndex)
                    .findFirst()
                    .orElse(null);
            if (nextPattern != null && nextPattern.getDayType() == DayType.WORKOUT) {
                LocalDate nextDeadline = fromDate.plusDays(i);
                String nextWorkoutId = nextPattern.getWorkoutId();
                return new NextWorkout(nextWorkoutId, nextDeadline);
            }
        }
        return new NextWorkout(null, fromDate.plusDays(1));
    }
}

