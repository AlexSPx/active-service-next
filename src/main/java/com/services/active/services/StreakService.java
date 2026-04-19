package com.services.active.services;

import com.services.active.exceptions.NotFoundException;
import com.services.active.models.Routine;
import com.services.active.models.RoutinePattern;
import com.services.active.models.user.User;
import com.services.active.models.user.UserWeeklyCompletedWorkout;
import com.services.active.models.types.DayType;
import com.services.active.models.types.RoutineType;
import com.services.active.models.types.StreakUpdateStatus;
import com.services.active.dto.StreakUpdateResponse;
import com.services.active.repository.RoutineRepository;
import com.services.active.repository.RoutinePatternRepository;
import com.services.active.repository.UserRepository;
import com.services.active.repository.UserWeeklyCompletedWorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StreakService {

    private final RoutineRepository routineRepository;
    private final RoutinePatternRepository routinePatternRepository;
    private final UserRepository userRepository;
    private final UserWeeklyCompletedWorkoutRepository weeklyCompletedRepo;

    @Transactional
    public void checkStreak(User user) {
        LocalDate deadline = user.getNextWorkoutDeadline();
        if (deadline == null) return;

        LocalDate today = LocalDate.now();
        if (!today.isAfter(deadline)) return;

        log.info("Checking overdue streak state (userId={}, deadline={}, today={})", user.getId(), deadline, today);

        // Check if this is a WEEKLY_COMPLETION routine
        Optional<Routine> activeRoutine = getActiveRoutine(user);
        if (activeRoutine.isPresent() && activeRoutine.get().getRoutineType() == RoutineType.WEEKLY_COMPLETION) {
            // For weekly routines, reset the weekly tracking and handle streak
            if (user.getStreakFreezeCount() > 0) {
                user.setStreakFreezeCount(user.getStreakFreezeCount() - 1);
            } else {
                user.setCurrentStreak(0);
            }
            LocalDate currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            user.setCurrentWeekStart(currentMonday);
            // Reset weekly completed workouts
            weeklyCompletedRepo.deleteByUserId(user.getId());
            user.setNextWorkoutDeadline(currentMonday.plusDays(6));
            user.setNextWorkoutId(null);
            log.info("Reset weekly streak tracking after missed deadline (userId={}, freezeRemaining={}, currentStreak={})",
                    user.getId(), user.getStreakFreezeCount(), user.getCurrentStreak());
        } else {
            // SEQUENTIAL routine logic
            if (user.getStreakFreezeCount() > 0) {
                user.setStreakFreezeCount(user.getStreakFreezeCount() - 1);
                NextWorkout next = calculateNextWorkoutDay(user, deadline);
                user.setNextWorkoutId(next.workoutId());
                user.setNextWorkoutDeadline(next.deadline());
                log.info("Consumed streak freeze after missed deadline (userId={}, freezeRemaining={}, nextWorkoutId={}, nextDeadline={})",
                        user.getId(), user.getStreakFreezeCount(), user.getNextWorkoutId(), user.getNextWorkoutDeadline());
            } else {
                user.setCurrentStreak(0);
                user.setNextWorkoutId(null);
                user.setNextWorkoutDeadline(null);
                log.info("Streak reset after missed deadline with no freeze available (userId={})", user.getId());
            }
        }
        userRepository.save(user);
    }

    @Transactional
    public StreakUpdateResponse onWorkoutCompleted(String workosId, UUID completedWorkoutId) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        log.info("Processing completed workout for streak update (userId={}, workoutId={})", user.getId(), completedWorkoutId);

        LocalDate today = LocalDate.now();

        // Check if we should use weekly completion logic
        Optional<Routine> activeRoutine = getActiveRoutine(user);
        if (activeRoutine.isPresent() && activeRoutine.get().getRoutineType() == RoutineType.WEEKLY_COMPLETION) {
            StreakUpdateResponse response = onWeeklyWorkoutCompleted(user, activeRoutine.get(), completedWorkoutId, today);
            log.info("Finished weekly streak update (userId={}, status={}, currentStreak={}, nextWorkoutId={}, nextDeadline={})",
                    user.getId(), response.getStatus(), response.getCurrentStreak(), response.getNextWorkoutId(), response.getNextWorkoutDeadline());
            return response;
        }

        // SEQUENTIAL routine logic (original behavior)
        StreakUpdateResponse response = onSequentialWorkoutCompleted(user, completedWorkoutId, today);
        log.info("Finished sequential streak update (userId={}, status={}, currentStreak={}, nextWorkoutId={}, nextDeadline={})",
                user.getId(), response.getStatus(), response.getCurrentStreak(), response.getNextWorkoutId(), response.getNextWorkoutDeadline());
        return response;
    }

    private StreakUpdateResponse onSequentialWorkoutCompleted(User user, UUID completedWorkoutId, LocalDate today) {
        // Block multiple streak counts within the same calendar day
        if (user.getLastWorkoutCountedDate() != null && today.isEqual(user.getLastWorkoutCountedDate())) {
            log.warn("Ignoring workout completion because streak was already counted today (userId={}, workoutId={}, date={})",
                    user.getId(), completedWorkoutId, today);
            return snapshot(user, StreakUpdateStatus.WRONG_WORKOUT);
        }

        UUID expectedWorkoutId = user.getNextWorkoutId();

        if (expectedWorkoutId != null && !expectedWorkoutId.equals(completedWorkoutId)) {
            log.warn("Ignoring workout completion because it does not match expected workout (userId={}, expectedWorkoutId={}, receivedWorkoutId={})",
                    user.getId(), expectedWorkoutId, completedWorkoutId);
            return snapshot(user, StreakUpdateStatus.WRONG_WORKOUT);
        }

        LocalDate deadline = user.getNextWorkoutDeadline();
        StreakUpdateStatus status;
        if (deadline == null) {
            user.setCurrentStreak(1);
            if (user.getCurrentStreak() > user.getLongestStreak()) {
                user.setLongestStreak(user.getCurrentStreak());
            }
            NextWorkout next = calculateNextWorkoutDay(user, today);
            user.setNextWorkoutId(next.workoutId());
            user.setNextWorkoutDeadline(next.deadline());
            user.setLastWorkoutCountedDate(today);
            userRepository.save(user);
            status = StreakUpdateStatus.STARTED;
            log.info("Started streak from first counted workout (userId={}, nextWorkoutId={}, nextDeadline={})",
                    user.getId(), user.getNextWorkoutId(), user.getNextWorkoutDeadline());
            return snapshot(user, status);
        }

        if (today.isAfter(deadline)) {
            user.setCurrentStreak(1);
            if (user.getCurrentStreak() > user.getLongestStreak()) {
                user.setLongestStreak(user.getCurrentStreak());
            }
            NextWorkout next = calculateNextWorkoutDay(user, today);
            user.setNextWorkoutId(next.workoutId());
            user.setNextWorkoutDeadline(next.deadline());
            user.setLastWorkoutCountedDate(today);
            userRepository.save(user);
            status = StreakUpdateStatus.BROKEN_RESET;
            log.info("Reset and restarted streak after late completion (userId={}, nextWorkoutId={}, nextDeadline={})",
                    user.getId(), user.getNextWorkoutId(), user.getNextWorkoutDeadline());
        } else {
            int prev = user.getCurrentStreak();
            user.setCurrentStreak(prev + 1);
            if (user.getCurrentStreak() > user.getLongestStreak()) {
                user.setLongestStreak(user.getCurrentStreak());
            }
            NextWorkout next = calculateNextWorkoutDay(user, today);
            user.setNextWorkoutId(next.workoutId());
            user.setNextWorkoutDeadline(next.deadline());
            user.setLastWorkoutCountedDate(today);
            userRepository.save(user);
            status = (prev == 0) ? StreakUpdateStatus.STARTED : StreakUpdateStatus.CONTINUED;
            log.info("Advanced streak after on-time completion (userId={}, previousStreak={}, currentStreak={}, nextWorkoutId={}, nextDeadline={})",
                    user.getId(), prev, user.getCurrentStreak(), user.getNextWorkoutId(), user.getNextWorkoutDeadline());
        }

        return snapshot(user, status);
    }

    private StreakUpdateResponse onWeeklyWorkoutCompleted(User user, Routine routine,
                                                          UUID completedWorkoutId, LocalDate today) {
        LocalDate currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = currentMonday.plusDays(6);

        // Check if we need to reset weekly tracking (new week started)
        if (user.getCurrentWeekStart() == null || !user.getCurrentWeekStart().equals(currentMonday)) {
            if (user.getCurrentWeekStart() != null) {
                LocalDate previousWeekMonday = user.getCurrentWeekStart();
                LocalDate expectedNextMonday = previousWeekMonday.plusWeeks(1);

                if (currentMonday.isAfter(expectedNextMonday)) {
                    if (user.getStreakFreezeCount() > 0) {
                        user.setStreakFreezeCount(user.getStreakFreezeCount() - 1);
                    } else {
                        user.setCurrentStreak(0);
                    }
                }
            }
            user.setCurrentWeekStart(currentMonday);
            weeklyCompletedRepo.deleteByUserId(user.getId());
            log.info("Initialized weekly streak tracking window (userId={}, weekStart={}, weekEnd={})", user.getId(), currentMonday, endOfWeek);
        }

        // Get all required workout IDs from the routine pattern
        List<RoutinePattern> patterns = routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(routine.getId());
        Set<UUID> requiredWorkoutIds = patterns.stream()
                .filter(p -> p.getDayType() == DayType.WORKOUT)
                .map(RoutinePattern::getWorkoutId)
                .collect(Collectors.toSet());
        int requiredCount = requiredWorkoutIds.size();

        // Check if the completed workout is part of this routine
        if (!requiredWorkoutIds.contains(completedWorkoutId)) {
            log.warn("Ignoring weekly completion because workout is not in active routine pattern (userId={}, workoutId={})",
                    user.getId(), completedWorkoutId);
            return snapshot(user, StreakUpdateStatus.WRONG_WORKOUT, requiredCount);
        }

        // Check if this workout was already completed this week
        if (weeklyCompletedRepo.existsByUserIdAndWorkoutId(user.getId(), completedWorkoutId)) {
            log.warn("Ignoring weekly completion because workout is already recorded this week (userId={}, workoutId={})",
                    user.getId(), completedWorkoutId);
            return snapshot(user, StreakUpdateStatus.WRONG_WORKOUT, requiredCount);
        }

        // Mark this workout as completed
        weeklyCompletedRepo.save(UserWeeklyCompletedWorkout.builder()
                .userId(user.getId())
                .workoutId(completedWorkoutId)
                .build());
        user.setLastWorkoutCountedDate(today);

        // Get currently completed IDs
        Set<UUID> completedIds = weeklyCompletedRepo.findAllByUserId(user.getId()).stream()
                .map(UserWeeklyCompletedWorkout::getWorkoutId)
                .collect(Collectors.toSet());

        // Check if all workouts for the week are now complete
        if (completedIds.containsAll(requiredWorkoutIds)) {
            int prev = user.getCurrentStreak();
            user.setCurrentStreak(prev + 1);
            if (user.getCurrentStreak() > user.getLongestStreak()) {
                user.setLongestStreak(user.getCurrentStreak());
            }
            LocalDate nextMonday = currentMonday.plusWeeks(1);
            user.setNextWorkoutDeadline(nextMonday.plusDays(6));
            user.setNextWorkoutId(null);

            userRepository.save(user);
            log.info("Completed all required weekly workouts and advanced streak (userId={}, requiredWorkouts={}, currentStreak={}, nextDeadline={})",
                    user.getId(), requiredCount, user.getCurrentStreak(), user.getNextWorkoutDeadline());
            return snapshot(user, prev == 0 ? StreakUpdateStatus.STARTED : StreakUpdateStatus.CONTINUED, requiredCount);
        } else {
            Set<UUID> remaining = new HashSet<>(requiredWorkoutIds);
            remaining.removeAll(completedIds);
            user.setNextWorkoutId(remaining.iterator().next());
            user.setNextWorkoutDeadline(endOfWeek);

            userRepository.save(user);
            log.info("Recorded weekly workout progress (userId={}, completedWorkouts={}, requiredWorkouts={}, nextWorkoutId={}, nextDeadline={})",
                    user.getId(), completedIds.size(), requiredCount, user.getNextWorkoutId(), user.getNextWorkoutDeadline());
            return snapshot(user, StreakUpdateStatus.WEEKLY_PROGRESS, requiredCount);
        }
    }

    private Optional<Routine> getActiveRoutine(User user) {
        UUID activeId = user.getActiveRoutineId();
        if (activeId == null) {
            return Optional.empty();
        }
        return routineRepository.findByIdAndUserId(activeId, user.getId());
    }

    private StreakUpdateResponse snapshot(User u, StreakUpdateStatus status) {
        Set<String> weeklyIds = weeklyCompletedRepo.findAllByUserId(u.getId()).stream()
                .map(w -> w.getWorkoutId().toString())
                .collect(Collectors.toSet());
        return StreakUpdateResponse.builder()
                .status(status)
                .currentStreak(u.getCurrentStreak())
                .longestStreak(u.getLongestStreak())
                .nextWorkoutId(u.getNextWorkoutId() != null ? u.getNextWorkoutId().toString() : null)
                .nextWorkoutDeadline(u.getNextWorkoutDeadline())
                .streakFreezeCount(u.getStreakFreezeCount())
                .weeklyCompletedWorkoutIds(weeklyIds)
                .build();
    }

    private StreakUpdateResponse snapshot(User u, StreakUpdateStatus status, int weeklyWorkoutsRequired) {
        Set<String> weeklyIds = weeklyCompletedRepo.findAllByUserId(u.getId()).stream()
                .map(w -> w.getWorkoutId().toString())
                .collect(Collectors.toSet());
        return StreakUpdateResponse.builder()
                .status(status)
                .currentStreak(u.getCurrentStreak())
                .longestStreak(u.getLongestStreak())
                .nextWorkoutId(u.getNextWorkoutId() != null ? u.getNextWorkoutId().toString() : null)
                .nextWorkoutDeadline(u.getNextWorkoutDeadline())
                .streakFreezeCount(u.getStreakFreezeCount())
                .weeklyCompletedWorkoutIds(weeklyIds)
                .weeklyWorkoutsRequired(weeklyWorkoutsRequired)
                .build();
    }

    private record NextWorkout(UUID workoutId, LocalDate deadline) {}

    private NextWorkout calculateNextWorkoutDay(User user, LocalDate fromDate) {
        UUID activeId = user.getActiveRoutineId();
        if (activeId == null) {
            return new NextWorkout(null, fromDate.plusDays(1));
        }
        Optional<Routine> optRoutine = routineRepository.findByIdAndUserId(activeId, user.getId());
        if (optRoutine.isEmpty()) {
            return new NextWorkout(null, fromDate.plusDays(1));
        }
        Routine activeRoutine = optRoutine.get();
        List<RoutinePattern> pattern = routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(activeRoutine.getId());
        if (pattern == null || pattern.isEmpty()) {
            return new NextWorkout(null, fromDate.plusDays(1));
        }

        LocalDate startDate = Optional.ofNullable(activeRoutine.getCreatedAt())
                .map(LocalDateTime::toLocalDate)
                .orElse(fromDate);

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
                UUID nextWorkoutId = nextPattern.getWorkoutId();
                return new NextWorkout(nextWorkoutId, nextDeadline);
            }
        }
        return new NextWorkout(null, fromDate.plusDays(1));
    }
}
