package com.services.active.services;

import com.services.active.models.Routine;
import com.services.active.models.RoutinePattern;
import com.services.active.models.user.StreakInfo;
import com.services.active.models.user.User;
import com.services.active.models.types.DayType;
import com.services.active.models.types.RoutineType;
import com.services.active.models.types.StreakUpdateStatus;
import com.services.active.repository.RoutineRepository;
import com.services.active.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StreakServiceTest {

    @Mock
    private RoutineRepository routineRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StreakService streakService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        streakService = new StreakService(routineRepository, userRepository);
    }

    @Test
    void checkStreak_noDeadline_noOp() {
        User user = User.builder().id("u1").build();
        user.setStreak(new StreakInfo());
        streakService.checkStreak(user);
        verify(userRepository, never()).save(any());
    }

    @Test
    void checkStreak_missedDeadline_withFreeze_consumesFreezeAndAdvances() {
        User user = User.builder().id("u1").activeRoutineId("r1").build();
        user.setStreak(StreakInfo.builder()
                .nextWorkoutDeadline(LocalDate.now().minusDays(1))
                .streakFreezeCount(1)
                .build());
        Routine routine = Routine.builder()
                .id("r1").userId("u1")
                .createdAt(LocalDateTime.now().minusDays(7))
                .pattern(List.of(
                        RoutinePattern.builder().dayIndex(0).dayType(DayType.WORKOUT).workoutId("W0").build(),
                        RoutinePattern.builder().dayIndex(1).dayType(DayType.REST).build()))
                .build();
        when(routineRepository.findByIdAndUserId("r1", "u1")).thenReturn(Optional.of(routine));

        streakService.checkStreak(user);

        assertEquals(0, user.getStreak().getCurrentStreak());
        assertEquals(0, user.getStreak().getStreakFreezeCount());
        assertNotNull(user.getStreak().getNextWorkoutDeadline());
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void checkStreak_missedDeadline_noFreeze_breaksStreak() {
        User user = User.builder().id("u1").build();
        user.setStreak(StreakInfo.builder()
                .currentStreak(5)
                .nextWorkoutDeadline(LocalDate.now().minusDays(1))
                .streakFreezeCount(0)
                .build());

        streakService.checkStreak(user);

        assertEquals(0, user.getStreak().getCurrentStreak());
        assertNull(user.getStreak().getNextWorkoutId());
        assertNull(user.getStreak().getNextWorkoutDeadline());
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void onWorkoutCompleted_wrongWorkout_noAdvance() {
        String workosId = "workos-u1";
        String dbUserId = "u1";

        User user = User.builder().id(dbUserId).workosId(workosId).build();
        user.setStreak(StreakInfo.builder()
                .nextWorkoutId("W1")
                .nextWorkoutDeadline(LocalDate.now())
                .build());
        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));

        streakService.onWorkoutCompleted(workosId, "W2");

        assertEquals(0, user.getStreak().getCurrentStreak());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void onWorkoutCompleted_correctOnTime_advancesAndSetsNext() {
        String workosId = "workos-u1";
        String dbUserId = "u1";

        User user = User.builder().id(dbUserId).workosId(workosId).activeRoutineId("r1").build();
        user.setStreak(StreakInfo.builder()
                .currentStreak(2)
                .longestStreak(3)
                .nextWorkoutId("W0")
                .nextWorkoutDeadline(LocalDate.now())
                .build());
        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));
        Routine routine = Routine.builder()
                .id("r1").userId(dbUserId)
                .createdAt(LocalDateTime.now().minusDays(7))
                .pattern(List.of(
                        RoutinePattern.builder().dayIndex(0).dayType(DayType.WORKOUT).workoutId("W0").build(),
                        RoutinePattern.builder().dayIndex(1).dayType(DayType.REST).build(),
                        RoutinePattern.builder().dayIndex(2).dayType(DayType.WORKOUT).workoutId("W2").build()))
                .build();
        when(routineRepository.findByIdAndUserId("r1", dbUserId)).thenReturn(Optional.of(routine));

        streakService.onWorkoutCompleted(workosId, "W0");

        assertEquals(3, user.getStreak().getCurrentStreak());
        assertEquals(3, user.getStreak().getLongestStreak());
        assertNotNull(user.getStreak().getNextWorkoutDeadline());
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void doubleCompletionSameDay_doesNotIncrement() {
        String workosId = "workos-u1";
        String dbUserId = "u1";

        User user = User.builder().id(dbUserId).workosId(workosId).activeRoutineId(null).build();
        user.setStreak(new StreakInfo());
        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));

        // First completion starts the streak
        var first = streakService.onWorkoutCompleted(workosId, "anyWorkout");
        assertEquals(StreakUpdateStatus.STARTED, first.getStatus());
        assertEquals(1, user.getStreak().getCurrentStreak());

        // Second completion same day should not increment; returns WRONG_WORKOUT and no save
        var second = streakService.onWorkoutCompleted(workosId, "anyWorkout");
        assertEquals(StreakUpdateStatus.WRONG_WORKOUT, second.getStatus());
        assertEquals(1, user.getStreak().getCurrentStreak());
        // save invoked only once during the first update
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void weeklyCompletion_firstWorkout_returnsWeeklyProgress() {
        String workosId = "workos-u1";
        String dbUserId = "u1";

        User user = User.builder().id(dbUserId).workosId(workosId).activeRoutineId("r1").build();
        user.setStreak(new StreakInfo());

        Routine routine = Routine.builder()
                .id("r1").userId(dbUserId)
                .routineType(RoutineType.WEEKLY_COMPLETION)
                .pattern(List.of(
                        RoutinePattern.builder().dayIndex(0).dayType(DayType.WORKOUT).workoutId("A").build(),
                        RoutinePattern.builder().dayIndex(1).dayType(DayType.WORKOUT).workoutId("B").build(),
                        RoutinePattern.builder().dayIndex(2).dayType(DayType.WORKOUT).workoutId("C").build()))
                .build();

        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));
        when(routineRepository.findByIdAndUserId("r1", dbUserId)).thenReturn(Optional.of(routine));

        var result = streakService.onWorkoutCompleted(workosId, "A");

        assertEquals(StreakUpdateStatus.WEEKLY_PROGRESS, result.getStatus());
        assertEquals(0, result.getCurrentStreak()); // Streak not incremented until all workouts done
        assertEquals(3, result.getWeeklyWorkoutsRequired());
        assertTrue(result.getWeeklyCompletedWorkoutIds().contains("A"));
        assertEquals(1, result.getWeeklyCompletedWorkoutIds().size());
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void weeklyCompletion_allWorkoutsCompleted_incrementsStreak() {
        String workosId = "workos-u1";
        String dbUserId = "u1";
        LocalDate today = LocalDate.now();
        LocalDate currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        User user = User.builder().id(dbUserId).workosId(workosId).activeRoutineId("r1").build();
        user.setStreak(StreakInfo.builder()
                .currentStreak(0)
                .currentWeekStart(currentMonday)
                .weeklyCompletedWorkoutIds(new HashSet<>(Set.of("A", "B"))) // Already completed A and B
                .build());

        Routine routine = Routine.builder()
                .id("r1").userId(dbUserId)
                .routineType(RoutineType.WEEKLY_COMPLETION)
                .pattern(List.of(
                        RoutinePattern.builder().dayIndex(0).dayType(DayType.WORKOUT).workoutId("A").build(),
                        RoutinePattern.builder().dayIndex(1).dayType(DayType.WORKOUT).workoutId("B").build(),
                        RoutinePattern.builder().dayIndex(2).dayType(DayType.WORKOUT).workoutId("C").build()))
                .build();

        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));
        when(routineRepository.findByIdAndUserId("r1", dbUserId)).thenReturn(Optional.of(routine));

        // Complete the final workout C
        var result = streakService.onWorkoutCompleted(workosId, "C");

        assertEquals(StreakUpdateStatus.STARTED, result.getStatus()); // First streak starts
        assertEquals(1, result.getCurrentStreak());
        assertEquals(3, result.getWeeklyWorkoutsRequired());
        assertTrue(result.getWeeklyCompletedWorkoutIds().containsAll(Set.of("A", "B", "C")));
        // Next deadline should be end of next week
        assertEquals(currentMonday.plusWeeks(1).plusDays(6), result.getNextWorkoutDeadline());
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void weeklyCompletion_continuesExistingStreak() {
        String workosId = "workos-u1";
        String dbUserId = "u1";
        LocalDate today = LocalDate.now();
        LocalDate currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        User user = User.builder().id(dbUserId).workosId(workosId).activeRoutineId("r1").build();
        user.setStreak(StreakInfo.builder()
                .currentStreak(5) // Existing streak
                .longestStreak(10)
                .currentWeekStart(currentMonday)
                .weeklyCompletedWorkoutIds(new HashSet<>(Set.of("A", "B")))
                .build());

        Routine routine = Routine.builder()
                .id("r1").userId(dbUserId)
                .routineType(RoutineType.WEEKLY_COMPLETION)
                .pattern(List.of(
                        RoutinePattern.builder().dayIndex(0).dayType(DayType.WORKOUT).workoutId("A").build(),
                        RoutinePattern.builder().dayIndex(1).dayType(DayType.WORKOUT).workoutId("B").build(),
                        RoutinePattern.builder().dayIndex(2).dayType(DayType.WORKOUT).workoutId("C").build()))
                .build();

        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));
        when(routineRepository.findByIdAndUserId("r1", dbUserId)).thenReturn(Optional.of(routine));

        var result = streakService.onWorkoutCompleted(workosId, "C");

        assertEquals(StreakUpdateStatus.CONTINUED, result.getStatus());
        assertEquals(6, result.getCurrentStreak());
        assertEquals(10, result.getLongestStreak()); // Unchanged
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void weeklyCompletion_duplicateWorkoutSameWeek_returnsWrongWorkout() {
        String workosId = "workos-u1";
        String dbUserId = "u1";
        LocalDate today = LocalDate.now();
        LocalDate currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        User user = User.builder().id(dbUserId).workosId(workosId).activeRoutineId("r1").build();
        user.setStreak(StreakInfo.builder()
                .currentWeekStart(currentMonday)
                .weeklyCompletedWorkoutIds(new HashSet<>(Set.of("A"))) // A already completed
                .build());

        Routine routine = Routine.builder()
                .id("r1").userId(dbUserId)
                .routineType(RoutineType.WEEKLY_COMPLETION)
                .pattern(List.of(
                        RoutinePattern.builder().dayIndex(0).dayType(DayType.WORKOUT).workoutId("A").build(),
                        RoutinePattern.builder().dayIndex(1).dayType(DayType.WORKOUT).workoutId("B").build(),
                        RoutinePattern.builder().dayIndex(2).dayType(DayType.WORKOUT).workoutId("C").build()))
                .build();

        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));
        when(routineRepository.findByIdAndUserId("r1", dbUserId)).thenReturn(Optional.of(routine));

        // Try to complete A again
        var result = streakService.onWorkoutCompleted(workosId, "A");

        assertEquals(StreakUpdateStatus.WRONG_WORKOUT, result.getStatus());
        assertEquals(1, user.getStreak().getWeeklyCompletedWorkoutIds().size()); // Still just 1
        verify(userRepository, never()).save(any()); // No save on duplicate
    }

    @Test
    void weeklyCompletion_workoutNotInRoutine_returnsWrongWorkout() {
        String workosId = "workos-u1";
        String dbUserId = "u1";

        User user = User.builder().id(dbUserId).workosId(workosId).activeRoutineId("r1").build();
        user.setStreak(new StreakInfo());

        Routine routine = Routine.builder()
                .id("r1").userId(dbUserId)
                .routineType(RoutineType.WEEKLY_COMPLETION)
                .pattern(List.of(
                        RoutinePattern.builder().dayIndex(0).dayType(DayType.WORKOUT).workoutId("A").build(),
                        RoutinePattern.builder().dayIndex(1).dayType(DayType.WORKOUT).workoutId("B").build()))
                .build();

        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));
        when(routineRepository.findByIdAndUserId("r1", dbUserId)).thenReturn(Optional.of(routine));

        // Try to complete workout "X" which is not in the routine
        var result = streakService.onWorkoutCompleted(workosId, "X");

        assertEquals(StreakUpdateStatus.WRONG_WORKOUT, result.getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void weeklyCompletion_newWeekResetsTracking() {
        String workosId = "workos-u1";
        String dbUserId = "u1";
        LocalDate today = LocalDate.now();
        LocalDate currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate previousMonday = currentMonday.minusWeeks(1);

        User user = User.builder().id(dbUserId).workosId(workosId).activeRoutineId("r1").build();
        user.setStreak(StreakInfo.builder()
                .currentStreak(3)
                .currentWeekStart(previousMonday) // Last week
                .weeklyCompletedWorkoutIds(new HashSet<>(Set.of("A", "B", "C"))) // All completed last week
                .nextWorkoutDeadline(previousMonday.plusDays(13)) // End of this week
                .build());

        Routine routine = Routine.builder()
                .id("r1").userId(dbUserId)
                .routineType(RoutineType.WEEKLY_COMPLETION)
                .pattern(List.of(
                        RoutinePattern.builder().dayIndex(0).dayType(DayType.WORKOUT).workoutId("A").build(),
                        RoutinePattern.builder().dayIndex(1).dayType(DayType.WORKOUT).workoutId("B").build(),
                        RoutinePattern.builder().dayIndex(2).dayType(DayType.WORKOUT).workoutId("C").build()))
                .build();

        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));
        when(routineRepository.findByIdAndUserId("r1", dbUserId)).thenReturn(Optional.of(routine));

        // New week, complete workout A
        var result = streakService.onWorkoutCompleted(workosId, "A");

        assertEquals(StreakUpdateStatus.WEEKLY_PROGRESS, result.getStatus());
        assertEquals(3, result.getCurrentStreak()); // Streak maintained (consecutive weeks)
        assertEquals(currentMonday, user.getStreak().getCurrentWeekStart()); // Reset to new week
        assertEquals(1, result.getWeeklyCompletedWorkoutIds().size()); // Reset tracking, only A
        assertTrue(result.getWeeklyCompletedWorkoutIds().contains("A"));
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void weeklyCompletion_missedWeek_breaksStreak() {
        String workosId = "workos-u1";
        String dbUserId = "u1";
        LocalDate today = LocalDate.now();
        LocalDate currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate twoWeeksAgo = currentMonday.minusWeeks(2); // Missed last week entirely

        User user = User.builder().id(dbUserId).workosId(workosId).activeRoutineId("r1").build();
        user.setStreak(StreakInfo.builder()
                .currentStreak(5)
                .currentWeekStart(twoWeeksAgo) // Two weeks ago
                .weeklyCompletedWorkoutIds(new HashSet<>(Set.of("A", "B", "C")))
                .streakFreezeCount(0)
                .build());

        Routine routine = Routine.builder()
                .id("r1").userId(dbUserId)
                .routineType(RoutineType.WEEKLY_COMPLETION)
                .pattern(List.of(
                        RoutinePattern.builder().dayIndex(0).dayType(DayType.WORKOUT).workoutId("A").build(),
                        RoutinePattern.builder().dayIndex(1).dayType(DayType.WORKOUT).workoutId("B").build(),
                        RoutinePattern.builder().dayIndex(2).dayType(DayType.WORKOUT).workoutId("C").build()))
                .build();

        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));
        when(routineRepository.findByIdAndUserId("r1", dbUserId)).thenReturn(Optional.of(routine));

        var result = streakService.onWorkoutCompleted(workosId, "A");

        assertEquals(StreakUpdateStatus.WEEKLY_PROGRESS, result.getStatus());
        assertEquals(0, result.getCurrentStreak()); // Streak broken due to missed week
        assertEquals(currentMonday, user.getStreak().getCurrentWeekStart());
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void weeklyCompletion_missedWeek_withFreeze_preservesStreak() {
        String workosId = "workos-u1";
        String dbUserId = "u1";
        LocalDate today = LocalDate.now();
        LocalDate currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate twoWeeksAgo = currentMonday.minusWeeks(2);

        User user = User.builder().id(dbUserId).workosId(workosId).activeRoutineId("r1").build();
        user.setStreak(StreakInfo.builder()
                .currentStreak(5)
                .currentWeekStart(twoWeeksAgo)
                .weeklyCompletedWorkoutIds(new HashSet<>(Set.of("A", "B", "C")))
                .streakFreezeCount(1) // Has a freeze
                .build());

        Routine routine = Routine.builder()
                .id("r1").userId(dbUserId)
                .routineType(RoutineType.WEEKLY_COMPLETION)
                .pattern(List.of(
                        RoutinePattern.builder().dayIndex(0).dayType(DayType.WORKOUT).workoutId("A").build(),
                        RoutinePattern.builder().dayIndex(1).dayType(DayType.WORKOUT).workoutId("B").build(),
                        RoutinePattern.builder().dayIndex(2).dayType(DayType.WORKOUT).workoutId("C").build()))
                .build();

        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));
        when(routineRepository.findByIdAndUserId("r1", dbUserId)).thenReturn(Optional.of(routine));

        var result = streakService.onWorkoutCompleted(workosId, "A");

        assertEquals(StreakUpdateStatus.WEEKLY_PROGRESS, result.getStatus());
        assertEquals(5, result.getCurrentStreak()); // Streak preserved
        assertEquals(0, result.getStreakFreezeCount()); // Freeze consumed
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void checkStreak_weeklyCompletion_missedDeadline_breaksStreak() {
        LocalDate today = LocalDate.now();
        LocalDate currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        User user = User.builder().id("u1").activeRoutineId("r1").build();
        user.setStreak(StreakInfo.builder()
                .currentStreak(3)
                .nextWorkoutDeadline(today.minusDays(1)) // Missed deadline
                .streakFreezeCount(0)
                .build());

        Routine routine = Routine.builder()
                .id("r1").userId("u1")
                .routineType(RoutineType.WEEKLY_COMPLETION)
                .pattern(List.of(
                        RoutinePattern.builder().dayIndex(0).dayType(DayType.WORKOUT).workoutId("A").build()))
                .build();

        when(routineRepository.findByIdAndUserId("r1", "u1")).thenReturn(Optional.of(routine));

        streakService.checkStreak(user);

        assertEquals(0, user.getStreak().getCurrentStreak()); // Streak broken
        assertEquals(currentMonday, user.getStreak().getCurrentWeekStart());
        assertNotNull(user.getStreak().getWeeklyCompletedWorkoutIds());
        assertTrue(user.getStreak().getWeeklyCompletedWorkoutIds().isEmpty());
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void checkStreak_weeklyCompletion_missedDeadline_withFreeze_preservesStreak() {
        LocalDate today = LocalDate.now();
        LocalDate currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        User user = User.builder().id("u1").activeRoutineId("r1").build();
        user.setStreak(StreakInfo.builder()
                .currentStreak(3)
                .nextWorkoutDeadline(today.minusDays(1))
                .streakFreezeCount(2)
                .build());

        Routine routine = Routine.builder()
                .id("r1").userId("u1")
                .routineType(RoutineType.WEEKLY_COMPLETION)
                .pattern(List.of(
                        RoutinePattern.builder().dayIndex(0).dayType(DayType.WORKOUT).workoutId("A").build()))
                .build();

        when(routineRepository.findByIdAndUserId("r1", "u1")).thenReturn(Optional.of(routine));

        streakService.checkStreak(user);

        assertEquals(3, user.getStreak().getCurrentStreak()); // Streak preserved
        assertEquals(1, user.getStreak().getStreakFreezeCount()); // One freeze consumed
        assertEquals(currentMonday.plusDays(6), user.getStreak().getNextWorkoutDeadline()); // End of this week
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void weeklyCompletion_workoutsCanBeCompletedInAnyOrder() {
        String workosId = "workos-u1";
        String dbUserId = "u1";
        LocalDate today = LocalDate.now();
        LocalDate currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        User user = User.builder().id(dbUserId).workosId(workosId).activeRoutineId("r1").build();
        user.setStreak(StreakInfo.builder()
                .currentWeekStart(currentMonday)
                .weeklyCompletedWorkoutIds(new HashSet<>())
                .build());

        Routine routine = Routine.builder()
                .id("r1").userId(dbUserId)
                .routineType(RoutineType.WEEKLY_COMPLETION)
                .pattern(List.of(
                        RoutinePattern.builder().dayIndex(0).dayType(DayType.WORKOUT).workoutId("A").build(),
                        RoutinePattern.builder().dayIndex(1).dayType(DayType.WORKOUT).workoutId("B").build(),
                        RoutinePattern.builder().dayIndex(2).dayType(DayType.WORKOUT).workoutId("C").build()))
                .build();

        when(userRepository.findByWorkosId(workosId)).thenReturn(Optional.of(user));
        when(routineRepository.findByIdAndUserId("r1", dbUserId)).thenReturn(Optional.of(routine));

        // Complete workouts in reverse order: C, B, A
        var result1 = streakService.onWorkoutCompleted(workosId, "C");
        assertEquals(StreakUpdateStatus.WEEKLY_PROGRESS, result1.getStatus());
        assertTrue(result1.getWeeklyCompletedWorkoutIds().contains("C"));

        var result2 = streakService.onWorkoutCompleted(workosId, "B");
        assertEquals(StreakUpdateStatus.WEEKLY_PROGRESS, result2.getStatus());
        assertTrue(result2.getWeeklyCompletedWorkoutIds().containsAll(Set.of("B", "C")));

        var result3 = streakService.onWorkoutCompleted(workosId, "A");
        assertEquals(StreakUpdateStatus.STARTED, result3.getStatus()); // Streak started!
        assertEquals(1, result3.getCurrentStreak());
        assertTrue(result3.getWeeklyCompletedWorkoutIds().containsAll(Set.of("A", "B", "C")));
    }
}
