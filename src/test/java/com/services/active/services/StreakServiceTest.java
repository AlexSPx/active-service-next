package com.services.active.services;

import com.services.active.models.Routine;
import com.services.active.models.RoutinePattern;
import com.services.active.models.user.User;
import com.services.active.models.user.UserWeeklyCompletedWorkout;
import com.services.active.models.types.DayType;
import com.services.active.models.types.RoutineType;
import com.services.active.models.types.StreakUpdateStatus;
import com.services.active.repository.RoutineRepository;
import com.services.active.repository.RoutinePatternRepository;
import com.services.active.repository.UserRepository;
import com.services.active.repository.UserWeeklyCompletedWorkoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StreakServiceTest {

    @Mock private RoutineRepository routineRepository;
    @Mock private RoutinePatternRepository routinePatternRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserWeeklyCompletedWorkoutRepository weeklyCompletedRepo;

    private StreakService streakService;

    // Stable UUIDs used throughout tests
    private final UUID userId = UUID.randomUUID();
    private final UUID routineId = UUID.randomUUID();
    private final UUID workoutA = UUID.randomUUID();
    private final UUID workoutB = UUID.randomUUID();
    private final UUID workoutC = UUID.randomUUID();
    private final UUID workoutW0 = UUID.randomUUID();
    private final UUID workoutW2 = UUID.randomUUID();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        streakService = new StreakService(routineRepository, routinePatternRepository, userRepository, weeklyCompletedRepo);
    }

    @Test
    void checkStreak_noDeadline_noOp() {
        User user = User.builder().id(userId).build();
        streakService.checkStreak(user);
        verify(userRepository, never()).save(any());
    }

    @Test
    void checkStreak_missedDeadline_withFreeze_consumesFreezeAndAdvances() {
        User user = User.builder()
                .id(userId).activeRoutineId(routineId)
                .nextWorkoutDeadline(LocalDate.now().minusDays(1))
                .streakFreezeCount(1)
                .build();
        Routine routine = Routine.builder()
                .id(routineId).userId(userId)
                .createdAt(LocalDateTime.now().minusDays(7))
                .build();
        List<RoutinePattern> patterns = List.of(
                RoutinePattern.builder().routineId(routineId).dayIndex(0).dayType(DayType.WORKOUT).workoutId(workoutW0).build(),
                RoutinePattern.builder().routineId(routineId).dayIndex(1).dayType(DayType.REST).build());

        when(routineRepository.findByIdAndUserId(routineId, userId)).thenReturn(Optional.of(routine));
        when(routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(routineId)).thenReturn(patterns);

        streakService.checkStreak(user);

        assertEquals(0, user.getCurrentStreak());
        assertEquals(0, user.getStreakFreezeCount());
        assertNotNull(user.getNextWorkoutDeadline());
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void checkStreak_missedDeadline_noFreeze_breaksStreak() {
        User user = User.builder()
                .id(userId)
                .currentStreak(5)
                .nextWorkoutDeadline(LocalDate.now().minusDays(1))
                .streakFreezeCount(0)
                .build();

        streakService.checkStreak(user);

        assertEquals(0, user.getCurrentStreak());
        assertNull(user.getNextWorkoutId());
        assertNull(user.getNextWorkoutDeadline());
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void onWorkoutCompleted_wrongWorkout_noAdvance() {
        String workosId = "workos-u1";
        User user = User.builder()
                .id(userId).workosId(workosId)
                .nextWorkoutId(workoutW0)
                .nextWorkoutDeadline(LocalDate.now())
                .build();
        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));

        var result = streakService.onWorkoutCompleted(workosId, workoutW2);

        assertEquals(StreakUpdateStatus.WRONG_WORKOUT, result.getStatus());
        assertEquals(0, user.getCurrentStreak());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void onWorkoutCompleted_correctOnTime_advancesAndSetsNext() {
        String workosId = "workos-u1";
        User user = User.builder()
                .id(userId).workosId(workosId).activeRoutineId(routineId)
                .currentStreak(2).longestStreak(3)
                .nextWorkoutId(workoutW0)
                .nextWorkoutDeadline(LocalDate.now())
                .build();
        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));
        Routine routine = Routine.builder()
                .id(routineId).userId(userId)
                .createdAt(LocalDateTime.now().minusDays(7))
                .build();
        List<RoutinePattern> patterns = List.of(
                RoutinePattern.builder().routineId(routineId).dayIndex(0).dayType(DayType.WORKOUT).workoutId(workoutW0).build(),
                RoutinePattern.builder().routineId(routineId).dayIndex(1).dayType(DayType.REST).build(),
                RoutinePattern.builder().routineId(routineId).dayIndex(2).dayType(DayType.WORKOUT).workoutId(workoutW2).build());
        when(routineRepository.findByIdAndUserId(routineId, userId)).thenReturn(Optional.of(routine));
        when(routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(routineId)).thenReturn(patterns);

        streakService.onWorkoutCompleted(workosId, workoutW0);

        assertEquals(3, user.getCurrentStreak());
        assertEquals(3, user.getLongestStreak());
        assertNotNull(user.getNextWorkoutDeadline());
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void doubleCompletionSameDay_doesNotIncrement() {
        String workosId = "workos-u1";
        User user = User.builder()
                .id(userId).workosId(workosId)
                .build();
        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));
        when(weeklyCompletedRepo.findAllByUserId(userId)).thenReturn(List.of());

        var first = streakService.onWorkoutCompleted(workosId, workoutA);
        assertEquals(StreakUpdateStatus.STARTED, first.getStatus());
        assertEquals(1, user.getCurrentStreak());

        var second = streakService.onWorkoutCompleted(workosId, workoutA);
        assertEquals(StreakUpdateStatus.WRONG_WORKOUT, second.getStatus());
        assertEquals(1, user.getCurrentStreak());
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void weeklyCompletion_firstWorkout_returnsWeeklyProgress() {
        String workosId = "workos-u1";
        User user = User.builder()
                .id(userId).workosId(workosId).activeRoutineId(routineId)
                .build();

        Routine routine = Routine.builder()
                .id(routineId).userId(userId)
                .routineType(RoutineType.WEEKLY_COMPLETION)
                .build();
        List<RoutinePattern> patterns = List.of(
                RoutinePattern.builder().routineId(routineId).dayIndex(0).dayType(DayType.WORKOUT).workoutId(workoutA).build(),
                RoutinePattern.builder().routineId(routineId).dayIndex(1).dayType(DayType.WORKOUT).workoutId(workoutB).build(),
                RoutinePattern.builder().routineId(routineId).dayIndex(2).dayType(DayType.WORKOUT).workoutId(workoutC).build());

        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));
        when(routineRepository.findByIdAndUserId(routineId, userId)).thenReturn(Optional.of(routine));
        when(routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(routineId)).thenReturn(patterns);
        when(weeklyCompletedRepo.existsByUserIdAndWorkoutId(userId, workoutA)).thenReturn(false);
        when(weeklyCompletedRepo.findAllByUserId(userId)).thenReturn(List.of(
                UserWeeklyCompletedWorkout.builder().userId(userId).workoutId(workoutA).build()));

        var result = streakService.onWorkoutCompleted(workosId, workoutA);

        assertEquals(StreakUpdateStatus.WEEKLY_PROGRESS, result.getStatus());
        assertEquals(0, result.getCurrentStreak());
        assertEquals(3, result.getWeeklyWorkoutsRequired());
        assertTrue(result.getWeeklyCompletedWorkoutIds().contains(workoutA.toString()));
        assertEquals(1, result.getWeeklyCompletedWorkoutIds().size());
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void weeklyCompletion_allWorkoutsCompleted_incrementsStreak() {
        String workosId = "workos-u1";
        LocalDate today = LocalDate.now();
        LocalDate currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        User user = User.builder()
                .id(userId).workosId(workosId).activeRoutineId(routineId)
                .currentStreak(0)
                .currentWeekStart(currentMonday)
                .build();

        Routine routine = Routine.builder()
                .id(routineId).userId(userId)
                .routineType(RoutineType.WEEKLY_COMPLETION)
                .build();
        List<RoutinePattern> patterns = List.of(
                RoutinePattern.builder().routineId(routineId).dayIndex(0).dayType(DayType.WORKOUT).workoutId(workoutA).build(),
                RoutinePattern.builder().routineId(routineId).dayIndex(1).dayType(DayType.WORKOUT).workoutId(workoutB).build(),
                RoutinePattern.builder().routineId(routineId).dayIndex(2).dayType(DayType.WORKOUT).workoutId(workoutC).build());

        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));
        when(routineRepository.findByIdAndUserId(routineId, userId)).thenReturn(Optional.of(routine));
        when(routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(routineId)).thenReturn(patterns);
        when(weeklyCompletedRepo.existsByUserIdAndWorkoutId(userId, workoutC)).thenReturn(false);
        // After saving C, all three are completed
        when(weeklyCompletedRepo.findAllByUserId(userId)).thenReturn(List.of(
                UserWeeklyCompletedWorkout.builder().userId(userId).workoutId(workoutA).build(),
                UserWeeklyCompletedWorkout.builder().userId(userId).workoutId(workoutB).build(),
                UserWeeklyCompletedWorkout.builder().userId(userId).workoutId(workoutC).build()));

        var result = streakService.onWorkoutCompleted(workosId, workoutC);

        assertEquals(StreakUpdateStatus.STARTED, result.getStatus());
        assertEquals(1, result.getCurrentStreak());
        assertEquals(3, result.getWeeklyWorkoutsRequired());
        assertEquals(currentMonday.plusWeeks(1).plusDays(6), result.getNextWorkoutDeadline());
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void weeklyCompletion_duplicateWorkoutSameWeek_returnsWrongWorkout() {
        String workosId = "workos-u1";
        LocalDate currentMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        User user = User.builder()
                .id(userId).workosId(workosId).activeRoutineId(routineId)
                .currentWeekStart(currentMonday)
                .build();

        Routine routine = Routine.builder()
                .id(routineId).userId(userId)
                .routineType(RoutineType.WEEKLY_COMPLETION)
                .build();
        List<RoutinePattern> patterns = List.of(
                RoutinePattern.builder().routineId(routineId).dayIndex(0).dayType(DayType.WORKOUT).workoutId(workoutA).build(),
                RoutinePattern.builder().routineId(routineId).dayIndex(1).dayType(DayType.WORKOUT).workoutId(workoutB).build(),
                RoutinePattern.builder().routineId(routineId).dayIndex(2).dayType(DayType.WORKOUT).workoutId(workoutC).build());

        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));
        when(routineRepository.findByIdAndUserId(routineId, userId)).thenReturn(Optional.of(routine));
        when(routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(routineId)).thenReturn(patterns);
        when(weeklyCompletedRepo.existsByUserIdAndWorkoutId(userId, workoutA)).thenReturn(true); // Already done

        var result = streakService.onWorkoutCompleted(workosId, workoutA);

        assertEquals(StreakUpdateStatus.WRONG_WORKOUT, result.getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void weeklyCompletion_workoutNotInRoutine_returnsWrongWorkout() {
        String workosId = "workos-u1";

        User user = User.builder()
                .id(userId).workosId(workosId).activeRoutineId(routineId)
                .build();

        Routine routine = Routine.builder()
                .id(routineId).userId(userId)
                .routineType(RoutineType.WEEKLY_COMPLETION)
                .build();
        List<RoutinePattern> patterns = List.of(
                RoutinePattern.builder().routineId(routineId).dayIndex(0).dayType(DayType.WORKOUT).workoutId(workoutA).build(),
                RoutinePattern.builder().routineId(routineId).dayIndex(1).dayType(DayType.WORKOUT).workoutId(workoutB).build());

        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));
        when(routineRepository.findByIdAndUserId(routineId, userId)).thenReturn(Optional.of(routine));
        when(routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(routineId)).thenReturn(patterns);
        when(weeklyCompletedRepo.findAllByUserId(userId)).thenReturn(List.of());

        UUID unknownWorkout = UUID.randomUUID();
        var result = streakService.onWorkoutCompleted(workosId, unknownWorkout);

        assertEquals(StreakUpdateStatus.WRONG_WORKOUT, result.getStatus());
        verify(userRepository, never()).save(any());
    }
}
