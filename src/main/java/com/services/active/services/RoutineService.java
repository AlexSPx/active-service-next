package com.services.active.services;

import com.services.active.dto.CreateRoutineRequest;
import com.services.active.dto.RoutineResponse;
import com.services.active.dto.UpdateRoutineRequest;
import com.services.active.exceptions.ConflictException;
import com.services.active.exceptions.NotFoundException;
import com.services.active.exceptions.UnauthorizedException;
import com.services.active.exceptions.BadRequestException;
import com.services.active.models.Routine;
import com.services.active.models.RoutinePattern;
import com.services.active.models.types.DayType;
import com.services.active.models.types.RoutineType;
import com.services.active.models.user.User;
import com.services.active.repository.RoutineRepository;
import com.services.active.repository.RoutinePatternRepository;
import com.services.active.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoutineService {

    private final RoutineRepository routineRepository;
    private final RoutinePatternRepository routinePatternRepository;
    private final UserRepository userRepository;

    @Transactional
    public RoutineResponse createRoutine(String workosId, CreateRoutineRequest request) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UUID userId = user.getId();

        if (routineRepository.existsByUserIdAndNameIgnoreCase(userId, request.getName())) {
            throw new ConflictException("Routine name already exists");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean requestedActive = Boolean.TRUE.equals(request.getActive());
        RoutineType routineType = request.getRoutineType() != null
                ? request.getRoutineType()
                : RoutineType.SEQUENTIAL;
        Routine routine = Routine.builder()
                .name(request.getName())
                .description(request.getDescription())
                .userId(userId)
                .createdAt(now)
                .updatedAt(now)
                .startDate((request.getStartDate() != null
                        ? request.getStartDate()
                        : LocalDate.now())
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant())
                .routineType(routineType)
                .build();
        Routine saved = routineRepository.save(routine);

        // Save patterns
        if (request.getPattern() != null) {
            for (var p : request.getPattern()) {
                routinePatternRepository.save(RoutinePattern.builder()
                        .routineId(saved.getId())
                        .dayIndex(p.getDayIndex())
                        .dayType(p.getDayType())
                        .workoutId(p.getWorkoutId())
                        .build());
            }
        }

        if (requestedActive) {
            user.setActiveRoutineId(saved.getId());
            userRepository.save(user);
        }

        List<RoutinePattern> patterns = routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(saved.getId());
        return RoutineResponse.from(saved, patterns);
    }

    public List<RoutineResponse> listRoutines(String workosId) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        List<Routine> routines = routineRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId());
        return routines.stream()
                .map(r -> RoutineResponse.from(r, routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(r.getId())))
                .collect(Collectors.toList());
    }

    public RoutineResponse getRoutine(String workosId, String id) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Routine routine = routineRepository.findByIdAndUserId(UUID.fromString(id), user.getId())
                .orElseThrow(() -> new NotFoundException("Routine not found"));
        List<RoutinePattern> patterns = routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(routine.getId());
        return RoutineResponse.from(routine, patterns);
    }

    public RoutineResponse getActiveRoutine(String workosId) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UUID activeId = user.getActiveRoutineId();
        if (activeId == null) {
            throw new NotFoundException("No active routine");
        }
        Routine routine = routineRepository.findByIdAndUserId(activeId, user.getId())
                .orElseThrow(() -> new NotFoundException("Active routine not found"));
        List<RoutinePattern> patterns = routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(routine.getId());
        return RoutineResponse.from(routine, patterns);
    }

    @Transactional
    public RoutineResponse updateRoutine(String workosId, String id, UpdateRoutineRequest request) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UUID userId = user.getId();

        UUID routineId = UUID.fromString(id);
        Routine existing = routineRepository.findById(routineId)
                .orElseThrow(() -> new NotFoundException("Routine not found"));
        if (!userId.equals(existing.getUserId())) {
            throw new UnauthorizedException("Not authorized to update this routine");
        }
        boolean changed = false;
        if (request.getName() != null) {
            String newName = request.getName();
            if (!newName.equalsIgnoreCase(existing.getName())
                    && routineRepository.existsByUserIdAndNameIgnoreCase(userId, newName)) {
                throw new ConflictException("Routine name already exists");
            }
            existing.setName(newName);
            changed = true;
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
            changed = true;
        }
        if (request.getPattern() != null) {
            if (request.getPattern().isEmpty()) {
                throw new BadRequestException("Pattern is required");
            }
            // Replace patterns
            routinePatternRepository.deleteByRoutineId(routineId);
            for (var p : request.getPattern()) {
                routinePatternRepository.save(RoutinePattern.builder()
                        .routineId(routineId)
                        .dayIndex(p.getDayIndex())
                        .dayType(p.getDayType())
                        .workoutId(p.getWorkoutId())
                        .build());
            }
            changed = true;
        }
        if (request.getStartDate() != null) {
            existing.setStartDate(request.getStartDate().atStartOfDay(ZoneOffset.UTC).toInstant());
            changed = true;
        }
        if (request.getRoutineType() != null) {
            existing.setRoutineType(request.getRoutineType());
            if (request.getRoutineType() == RoutineType.WEEKLY_COMPLETION) {
                // Remove REST days from patterns
                List<RoutinePattern> patterns = routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(routineId);
                for (RoutinePattern p : patterns) {
                    if (p.getDayType() == DayType.REST) {
                        routinePatternRepository.delete(p);
                    }
                }
            }
            changed = true;
        }
        if (request.getActive() != null) {
            boolean makeActive = request.getActive();
            if (makeActive) {
                user.setActiveRoutineId(existing.getId());
                userRepository.save(user);
            } else {
                if (existing.getId().equals(user.getActiveRoutineId())) {
                    user.setActiveRoutineId(null);
                    userRepository.save(user);
                }
            }
        }
        if (changed) {
            existing.setUpdatedAt(LocalDateTime.now());
            routineRepository.save(existing);
        }
        List<RoutinePattern> patterns = routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(routineId);
        return RoutineResponse.from(existing, patterns);
    }

    @Transactional
    public void deleteRoutine(String workosId, String id) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UUID userId = user.getId();

        UUID routineId = UUID.fromString(id);
        Routine existing = routineRepository.findById(routineId)
                .orElseThrow(() -> new NotFoundException("Routine not found"));
        if (!userId.equals(existing.getUserId())) {
            throw new UnauthorizedException("Not authorized to delete this routine");
        }
        if (routineId.equals(user.getActiveRoutineId())) {
            user.setActiveRoutineId(null);
            userRepository.save(user);
        }
        routinePatternRepository.deleteByRoutineId(routineId);
        routineRepository.deleteById(routineId);
    }
}
