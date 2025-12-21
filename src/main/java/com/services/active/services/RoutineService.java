package com.services.active.services;

import com.services.active.dto.CreateRoutineRequest;
import com.services.active.dto.UpdateRoutineRequest;
import com.services.active.exceptions.ConflictException;
import com.services.active.exceptions.NotFoundException;
import com.services.active.exceptions.UnauthorizedException;
import com.services.active.exceptions.BadRequestException;
import com.services.active.models.Routine;
import com.services.active.models.user.User;
import com.services.active.repository.RoutineRepository;
import com.services.active.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoutineService {

    private final RoutineRepository routineRepository;
    private final UserRepository userRepository;

    public Routine createRoutine(String workosId, CreateRoutineRequest request) {
        // First get the user to obtain the database ID
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        String userId = user.getId();

        if (routineRepository.existsByUserIdAndNameIgnoreCase(userId, request.getName())) {
            throw new ConflictException("Routine name already exists");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean requestedActive = Boolean.TRUE.equals(request.getActive());
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
                .pattern(request.getPattern())
                .build();
        Routine saved = routineRepository.save(routine);

        if (requestedActive) {
            user.setActiveRoutineId(saved.getId());
            userRepository.save(user);
        }
        return saved;
    }

    public List<Routine> listRoutines(String workosId) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return routineRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId());
    }

    public Routine getRoutine(String workosId, String id) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return routineRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new NotFoundException("Routine not found"));
    }

    public Routine getActiveRoutine(String workosId) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        String activeId = user.getActiveRoutineId();
        if (activeId == null || activeId.isBlank()) {
            throw new NotFoundException("No active routine");
        }
        return routineRepository.findByIdAndUserId(activeId, user.getId())
                .orElseThrow(() -> new NotFoundException("Active routine not found"));
    }

    public Routine updateRoutine(String workosId, String id, UpdateRoutineRequest request) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        String userId = user.getId();

        Routine existing = routineRepository.findById(id)
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
            existing.setPattern(request.getPattern());
            changed = true;
        }
        if (request.getStartDate() != null) {
            existing.setStartDate(request.getStartDate().atStartOfDay(ZoneOffset.UTC).toInstant());
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
            return routineRepository.save(existing);
        }
        return existing;
    }

    public void deleteRoutine(String workosId, String id) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        String userId = user.getId();

        Routine existing = routineRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Routine not found"));
        if (!userId.equals(existing.getUserId())) {
            throw new UnauthorizedException("Not authorized to delete this routine");
        }
        if (id.equals(user.getActiveRoutineId())) {
            user.setActiveRoutineId(null);
            userRepository.save(user);
        }
        routineRepository.deleteById(id);
    }
}
