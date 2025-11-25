package com.services.active.services;

import com.services.active.models.Routine;
import com.services.active.models.RoutinePattern;
import com.services.active.models.user.StreakInfo;
import com.services.active.models.user.User;
import com.services.active.models.types.DayType;
import com.services.active.models.types.StreakUpdateStatus;
import com.services.active.repository.RoutineRepository;
import com.services.active.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
        User user = User.builder().id("u1").build();
        user.setStreak(StreakInfo.builder()
                .nextWorkoutId("W1")
                .nextWorkoutDeadline(LocalDate.now())
                .build());
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        streakService.onWorkoutCompleted("u1", "W2");

        assertEquals(0, user.getStreak().getCurrentStreak());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void onWorkoutCompleted_correctOnTime_advancesAndSetsNext() {
        User user = User.builder().id("u1").activeRoutineId("r1").build();
        user.setStreak(StreakInfo.builder()
                .currentStreak(2)
                .longestStreak(3)
                .nextWorkoutId("W0")
                .nextWorkoutDeadline(LocalDate.now())
                .build());
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        Routine routine = Routine.builder()
                .id("r1").userId("u1")
                .createdAt(LocalDateTime.now().minusDays(7))
                .pattern(List.of(
                        RoutinePattern.builder().dayIndex(0).dayType(DayType.WORKOUT).workoutId("W0").build(),
                        RoutinePattern.builder().dayIndex(1).dayType(DayType.REST).build(),
                        RoutinePattern.builder().dayIndex(2).dayType(DayType.WORKOUT).workoutId("W2").build()))
                .build();
        when(routineRepository.findByIdAndUserId("r1", "u1")).thenReturn(Optional.of(routine));

        streakService.onWorkoutCompleted("u1", "W0");

        assertEquals(3, user.getStreak().getCurrentStreak());
        assertEquals(3, user.getStreak().getLongestStreak());
        assertNotNull(user.getStreak().getNextWorkoutDeadline());
        verify(userRepository, times(1)).save(eq(user));
    }

    @Test
    void doubleCompletionSameDay_doesNotIncrement() {
        User user = User.builder().id("u1").activeRoutineId(null).build();
        user.setStreak(new StreakInfo());
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        // First completion starts the streak
        var first = streakService.onWorkoutCompleted("u1", "anyWorkout");
        assertEquals(StreakUpdateStatus.STARTED, first.getStatus());
        assertEquals(1, user.getStreak().getCurrentStreak());

        // Second completion same day should not increment; returns WRONG_WORKOUT and no save
        var second = streakService.onWorkoutCompleted("u1", "anyWorkout");
        assertEquals(StreakUpdateStatus.WRONG_WORKOUT, second.getStatus());
        assertEquals(1, user.getStreak().getCurrentStreak());
        // save invoked only once during the first update
        verify(userRepository, times(1)).save(eq(user));
    }
}
