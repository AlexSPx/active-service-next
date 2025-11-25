package com.services.active.services;

import com.services.active.exceptions.NotFoundException;
import com.services.active.models.Routine;
import com.services.active.models.RoutinePattern;
import com.services.active.models.user.StreakInfo;
import com.services.active.models.user.User;
import com.services.active.models.types.DayType;
import com.services.active.models.types.StreakUpdateStatus;
import com.services.active.dto.StreakUpdateResponse;
import com.services.active.repository.RoutineRepository;
import com.services.active.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
        userRepository.save(user);
    }

    public StreakUpdateResponse onWorkoutCompleted(String userId, String completedWorkoutId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        StreakInfo streak = safeStreak(user);
        LocalDate today = LocalDate.now();

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

