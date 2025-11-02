package com.services.active.services;

import com.services.active.dto.CreateRoutineRequest;
import com.services.active.dto.UpdateRoutineRequest;
import com.services.active.exceptions.ConflictException;
import com.services.active.exceptions.NotFoundException;
import com.services.active.exceptions.UnauthorizedException;
import com.services.active.models.Routine;
import com.services.active.models.User;
import com.services.active.repository.RoutineRepository;
import com.services.active.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoutineService {

    private final RoutineRepository routineRepository;
    private final UserRepository userRepository;

    public Routine createRoutine(String userId, CreateRoutineRequest request) {
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
                .pattern(request.getPattern())
                .build();
        Routine saved = routineRepository.save(routine);

        if (requestedActive) {
            User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
            user.setActiveRoutineId(saved.getId());
            userRepository.save(user);
        }
        return saved;
    }

    public List<Routine> listRoutines(String userId) {
        return routineRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    public Routine getRoutine(String userId, String id) {
        return routineRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Routine not found"));
    }

    public Routine getActiveRoutine(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        String activeId = user.getActiveRoutineId();
        if (activeId == null || activeId.isBlank()) {
            throw new NotFoundException("No active routine");
        }
        return routineRepository.findByIdAndUserId(activeId, userId)
                .orElseThrow(() -> new NotFoundException("Active routine not found"));
    }

    public Routine updateRoutine(String userId, String id, UpdateRoutineRequest request) {
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
            existing.setPattern(request.getPattern());
            changed = true;
        }
        if (request.getActive() != null) {
            User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
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

    public void deleteRoutine(String userId, String id) {
        Routine existing = routineRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Routine not found"));
        if (!userId.equals(existing.getUserId())) {
            throw new UnauthorizedException("Not authorized to delete this routine");
        }
        userRepository.findById(userId).ifPresent(user -> {
            if (id.equals(user.getActiveRoutineId())) {
                user.setActiveRoutineId(null);
                userRepository.save(user);
            }
        });
        routineRepository.deleteById(id);
    }
}
