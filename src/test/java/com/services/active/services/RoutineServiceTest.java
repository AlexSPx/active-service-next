package com.services.active.services;

import com.services.active.dto.CreateRoutineRequest;
import com.services.active.dto.UpdateRoutineRequest;
import com.services.active.exceptions.ConflictException;
import com.services.active.exceptions.NotFoundException;
import com.services.active.models.Routine;
import com.services.active.models.User;
import com.services.active.repository.RoutineRepository;
import com.services.active.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoutineServiceTest {

    @Mock
    private RoutineRepository routineRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private RoutineService routineService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        routineService = new RoutineService(routineRepository, userRepository);
    }

    @Test
    void createRoutine_conflictOnDuplicateName() {
        when(routineRepository.existsByUserIdAndNameIgnoreCase("user-1", "PPL")).thenReturn(true);
        CreateRoutineRequest req = CreateRoutineRequest.builder()
                .name("PPL")
                .pattern(java.util.List.of())
                .build();
        assertThrows(ConflictException.class, () -> routineService.createRoutine("user-1", req));
        verify(routineRepository, never()).save(any());
    }

    @Test
    void createRoutine_success_setsActivePointerWhenRequested() {
        CreateRoutineRequest req = CreateRoutineRequest.builder()
                .name("PPL")
                .pattern(java.util.List.of())
                .active(true)
                .build();
        Routine saved = Routine.builder().id("r-1").name("PPL").userId("user-1").pattern(java.util.List.of()).build();
        when(routineRepository.existsByUserIdAndNameIgnoreCase("user-1", "PPL")).thenReturn(false);
        when(routineRepository.save(any(Routine.class))).thenReturn(saved);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(User.builder().id("user-1").build()));

        Routine result = routineService.createRoutine("user-1", req);
        assertNotNull(result);
        assertEquals("r-1", result.getId());
        verify(userRepository, times(1)).save(argThat(u -> "r-1".equals(u.getActiveRoutineId())));
    }

    @Test
    void getRoutine_notFoundWhenWrongOwner() {
        when(routineRepository.findByIdAndUserId("r-1", "user-1")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> routineService.getRoutine("user-1", "r-1"));
    }

    @Test
    void updateRoutine_renameConflict() {
        Routine existing = Routine.builder().id("r-1").name("Old").userId("user-1").build();
        when(routineRepository.findById("r-1")).thenReturn(Optional.of(existing));
        when(routineRepository.existsByUserIdAndNameIgnoreCase("user-1", "New")).thenReturn(true);
        UpdateRoutineRequest req = UpdateRoutineRequest.builder().name("New").build();
        assertThrows(ConflictException.class, () -> routineService.updateRoutine("user-1", "r-1", req));
        verify(routineRepository, never()).save(any());
    }

    @Test
    void updateRoutine_setActiveTrue_updatesUserPointer() {
        Routine existing = Routine.builder().id("r-1").name("PPL").userId("user-1").build();
        when(routineRepository.findById("r-1")).thenReturn(Optional.of(existing));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(User.builder().id("user-1").build()));
        UpdateRoutineRequest req = UpdateRoutineRequest.builder().active(true).build();

        Routine result = routineService.updateRoutine("user-1", "r-1", req);
        assertNotNull(result);
        verify(userRepository, times(1)).save(argThat(u -> "r-1".equals(u.getActiveRoutineId())));
    }

    @Test
    void updateRoutine_setActiveFalse_clearsPointerIfMatches() {
        Routine existing = Routine.builder().id("r-1").name("PPL").userId("user-1").build();
        when(routineRepository.findById("r-1")).thenReturn(Optional.of(existing));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(User.builder().id("user-1").activeRoutineId("r-1").build()));
        UpdateRoutineRequest req = UpdateRoutineRequest.builder().active(false).build();

        Routine result = routineService.updateRoutine("user-1", "r-1", req);
        assertNotNull(result);
        verify(userRepository, times(1)).save(argThat(u -> u.getActiveRoutineId() == null));
    }

    @Test
    void deleteRoutine_clearsPointerIfDeletingActive() {
        Routine existing = Routine.builder().id("r-1").name("PPL").userId("user-1").build();
        when(routineRepository.findById("r-1")).thenReturn(Optional.of(existing));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(User.builder().id("user-1").activeRoutineId("r-1").build()));

        routineService.deleteRoutine("user-1", "r-1");

        verify(userRepository, times(1)).save(argThat(u -> u.getActiveRoutineId() == null));
        verify(routineRepository, times(1)).deleteById("r-1");
    }
}

