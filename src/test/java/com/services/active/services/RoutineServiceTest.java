package com.services.active.services;

import com.services.active.dto.CreateRoutineRequest;
import com.services.active.dto.UpdateRoutineRequest;
import com.services.active.exceptions.ConflictException;
import com.services.active.exceptions.NotFoundException;
import com.services.active.models.Routine;
import com.services.active.dto.RoutinePatternRequest;
import com.services.active.models.types.DayType;
import com.services.active.models.types.RoutineType;
import com.services.active.models.user.User;
import com.services.active.repository.RoutinePatternRepository;
import com.services.active.repository.RoutineRepository;
import com.services.active.repository.UserRepository;
import java.util.UUID;

import com.services.active.dto.RoutineResponse;
import com.services.active.models.RoutinePattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoutineServiceTest {

    @Mock
    private RoutineRepository routineRepository;
    @Mock
    private RoutinePatternRepository routinePatternRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private RoutineService routineService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        routineService = new RoutineService(routineRepository, routinePatternRepository, userRepository);
    }

    @Test
    void createRoutine_conflictOnDuplicateName() {
        String workosId = "workos-user-1";
        UUID dbUserId = UUID.randomUUID();

        when(userRepository.findByWorkosId(workosId))
                .thenReturn(Optional.of(User.builder().id(dbUserId).workosId(workosId).build()));
        when(routineRepository.existsByUserIdAndNameIgnoreCase(dbUserId, "PPL")).thenReturn(true);

        CreateRoutineRequest req = CreateRoutineRequest.builder()
                .name("PPL")
                //.pattern(java.util.List.of())
                .build();
        assertThrows(ConflictException.class, () -> routineService.createRoutine(workosId, req));
        verify(routineRepository, never()).save(any());
    }

    @Test
    void createRoutine_success_setsActivePointerWhenRequested() {
        String workosId = "workos-user-1";
        UUID dbUserId = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();

        CreateRoutineRequest req = CreateRoutineRequest.builder()
                .name("PPL")
                //.pattern(java.util.List.of())
                .active(true)
                .build();
        Routine saved = Routine.builder().id(r1).name("PPL").userId(dbUserId).build();

        when(userRepository.findByWorkosId(workosId))
                .thenReturn(Optional.of(User.builder().id(dbUserId).workosId(workosId).build()));
        when(routineRepository.existsByUserIdAndNameIgnoreCase(dbUserId, "PPL")).thenReturn(false);
        when(routineRepository.save(any(Routine.class))).thenReturn(saved);

        RoutineResponse result = routineService.createRoutine(workosId, req);
        assertNotNull(result);
        assertEquals(r1, result.getId());
        verify(userRepository, times(1)).save(argThat(u -> r1.equals(u.getActiveRoutineId())));
    }

    @Test
    void getRoutine_notFoundWhenWrongOwner() {
        String workosId = "workos-user-1";
        UUID dbUserId = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();

        when(userRepository.findByWorkosId(workosId))
                .thenReturn(Optional.of(User.builder().id(dbUserId).workosId(workosId).build()));
        when(routineRepository.findByIdAndUserId(r1, dbUserId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> routineService.getRoutine(workosId, r1.toString()));
    }

    @Test
    void updateRoutine_renameConflict() {
        String workosId = "workos-user-1";
        UUID dbUserId = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();

        Routine existing = Routine.builder().id(r1).name("Old").userId(dbUserId).build();
        when(userRepository.findByWorkosId(workosId))
                .thenReturn(Optional.of(User.builder().id(dbUserId).workosId(workosId).build()));
        when(routineRepository.findById(r1)).thenReturn(Optional.of(existing));
        when(routineRepository.existsByUserIdAndNameIgnoreCase(dbUserId, "New")).thenReturn(true);

        UpdateRoutineRequest req = UpdateRoutineRequest.builder().name("New").build();
        assertThrows(ConflictException.class, () -> routineService.updateRoutine(workosId, r1.toString(), req));
        verify(routineRepository, never()).save(any());
    }

    @Test
    void updateRoutine_setActiveTrue_updatesUserPointer() {
        String workosId = "workos-user-1";
        UUID dbUserId = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();

        Routine existing = Routine.builder().id(r1).name("PPL").userId(dbUserId).build();
        when(userRepository.findByWorkosId(workosId))
                .thenReturn(Optional.of(User.builder().id(dbUserId).workosId(workosId).build()));
        when(routineRepository.findById(r1)).thenReturn(Optional.of(existing));

        UpdateRoutineRequest req = UpdateRoutineRequest.builder().active(true).build();

        RoutineResponse result = routineService.updateRoutine(workosId, r1.toString(), req);
        assertNotNull(result);
        verify(userRepository, times(1)).save(argThat(u -> r1.equals(u.getActiveRoutineId())));
    }

    @Test
    void updateRoutine_setActiveFalse_clearsPointerIfMatches() {
        String workosId = "workos-user-1";
        UUID dbUserId = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();

        Routine existing = Routine.builder().id(r1).name("PPL").userId(dbUserId).build();
        when(userRepository.findByWorkosId(workosId))
                .thenReturn(Optional.of(User.builder().id(dbUserId).workosId(workosId).activeRoutineId(r1).build()));
        when(routineRepository.findById(r1)).thenReturn(Optional.of(existing));

        UpdateRoutineRequest req = UpdateRoutineRequest.builder().active(false).build();

        RoutineResponse result = routineService.updateRoutine(workosId, r1.toString(), req);
        assertNotNull(result);
        verify(userRepository, times(1)).save(argThat(u -> u.getActiveRoutineId() == null));
    }

    @Test
    void deleteRoutine_clearsPointerIfDeletingActive() {
        String workosId = "workos-user-1";
        UUID dbUserId = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();

        Routine existing = Routine.builder().id(r1).name("PPL").userId(dbUserId).build();
        when(userRepository.findByWorkosId(workosId))
                .thenReturn(Optional.of(User.builder().id(dbUserId).workosId(workosId).activeRoutineId(r1).build()));
        when(routineRepository.findById(r1)).thenReturn(Optional.of(existing));

        routineService.deleteRoutine(workosId, r1.toString());

        verify(userRepository, times(1)).save(argThat(u -> u.getActiveRoutineId() == null));
        verify(routineRepository, times(1)).deleteById(r1);
    }

    @Test
    void createRoutine_withWeeklyCompletionType() {
        String workosId = "workos-user-1";
        UUID dbUserId = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();

        CreateRoutineRequest req = CreateRoutineRequest.builder()
                .name("ABC Weekly")
                //.pattern(java.util.List.of())
                .routineType(RoutineType.WEEKLY_COMPLETION)
                .build();
        Routine saved = Routine.builder()
                .id(r1)
                .name("ABC Weekly")
                .userId(dbUserId)
                //.pattern(java.util.List.of())
                .routineType(RoutineType.WEEKLY_COMPLETION)
                .build();

        when(userRepository.findByWorkosId(workosId))
                .thenReturn(Optional.of(User.builder().id(dbUserId).workosId(workosId).build()));
        when(routineRepository.existsByUserIdAndNameIgnoreCase(dbUserId, "ABC Weekly")).thenReturn(false);
        when(routineRepository.save(argThat(r -> r.getRoutineType() == RoutineType.WEEKLY_COMPLETION))).thenReturn(saved);

        RoutineResponse result = routineService.createRoutine(workosId, req);
        assertNotNull(result);
        assertEquals(RoutineType.WEEKLY_COMPLETION, result.getRoutineType());
    }

    @Test
    void createRoutine_defaultsToSequentialType() {
        String workosId = "workos-user-1";
        UUID dbUserId = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();

        CreateRoutineRequest req = CreateRoutineRequest.builder()
                .name("PPL")
                //.pattern(java.util.List.of())
                .build();
        Routine saved = Routine.builder()
                .id(r1)
                .name("PPL")
                .userId(dbUserId)
                .routineType(RoutineType.SEQUENTIAL)
                .build();

        when(userRepository.findByWorkosId(workosId))
                .thenReturn(Optional.of(User.builder().id(dbUserId).workosId(workosId).build()));
        when(routineRepository.existsByUserIdAndNameIgnoreCase(dbUserId, "PPL")).thenReturn(false);
        when(routineRepository.save(argThat(r -> r.getRoutineType() == RoutineType.SEQUENTIAL))).thenReturn(saved);

        RoutineResponse result = routineService.createRoutine(workosId, req);
        assertNotNull(result);
        assertEquals(RoutineType.SEQUENTIAL, result.getRoutineType());
    }

    @Test
    void updateRoutine_changeToWeeklyCompletionType() {
        String workosId = "workos-user-1";
        UUID dbUserId = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();

        // Pattern with workouts and rest days (typical PPL split)
        List<RoutinePattern> patternsWithRest = List.of(
                RoutinePattern.builder().id(UUID.randomUUID()).routineId(r1).dayIndex(0).dayType(DayType.WORKOUT).workoutId(UUID.randomUUID()).build(),
                RoutinePattern.builder().id(UUID.randomUUID()).routineId(r1).dayIndex(1).dayType(DayType.WORKOUT).workoutId(UUID.randomUUID()).build(),
                RoutinePattern.builder().id(UUID.randomUUID()).routineId(r1).dayIndex(2).dayType(DayType.REST).build(),
                RoutinePattern.builder().id(UUID.randomUUID()).routineId(r1).dayIndex(3).dayType(DayType.WORKOUT).workoutId(UUID.randomUUID()).build(),
                RoutinePattern.builder().id(UUID.randomUUID()).routineId(r1).dayIndex(4).dayType(DayType.REST).build()
        );
        List<RoutinePattern> patternsWithoutRest = patternsWithRest.stream()
                .filter(p -> p.getDayType() == DayType.WORKOUT)
                .toList();

        Routine existing = Routine.builder()
                .id(r1)
                .name("PPL")
                .userId(dbUserId)
                .routineType(RoutineType.SEQUENTIAL)
                //.pattern(new java.util.ArrayList<>(patternWithRest))
                .build();
        when(userRepository.findByWorkosId(workosId))
                .thenReturn(Optional.of(User.builder().id(dbUserId).workosId(workosId).build()));
        when(routineRepository.findById(r1)).thenReturn(Optional.of(existing));
        when(routineRepository.save(any(Routine.class))).thenAnswer(inv -> inv.getArgument(0));
        when(routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(r1))
                .thenReturn(patternsWithRest)
                .thenReturn(patternsWithoutRest);

        UpdateRoutineRequest req = UpdateRoutineRequest.builder()
                .routineType(RoutineType.WEEKLY_COMPLETION)
                .build();

        RoutineResponse result = routineService.updateRoutine(workosId, r1.toString(), req);

        assertNotNull(result);
        assertEquals(RoutineType.WEEKLY_COMPLETION, result.getRoutineType());
        // REST days should be removed when changing to WEEKLY_COMPLETION
        List<RoutinePattern> patterns = routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(result.getId());
        assertEquals(3, patterns.size());
        assertTrue(patterns.stream().noneMatch(p -> p.getDayType() == DayType.REST));
        assertTrue(patterns.stream().allMatch(p -> p.getDayType() == DayType.WORKOUT));
        verify(routineRepository, times(1)).save(any(Routine.class));
    }
}
