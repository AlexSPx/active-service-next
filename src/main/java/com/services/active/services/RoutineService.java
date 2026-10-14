package com.services.active.services;

import com.services.active.dto.CreateRoutineRequest;
import com.services.active.dto.RoutineResponse;
import com.services.active.dto.RoutinePatternRequest;
import com.services.active.dto.UpdateRoutineRequest;
import com.services.active.dto.RoutineDeleteAck;
import com.services.active.exceptions.ConflictException;
import com.services.active.exceptions.NotFoundException;
import com.services.active.exceptions.UnauthorizedException;
import com.services.active.exceptions.BadRequestException;
import com.services.active.exceptions.StaleVersionException;
import com.services.active.models.Routine;
import com.services.active.models.RoutinePattern;
import com.services.active.models.types.DayType;
import com.services.active.models.types.RoutineType;
import com.services.active.models.user.User;
import com.services.active.repository.RoutineRepository;
import com.services.active.repository.RoutinePatternRepository;
import com.services.active.repository.UserRepository;
import com.services.active.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoutineService {

    private final RoutineRepository routineRepository;
    private final RoutinePatternRepository routinePatternRepository;
    private final UserRepository userRepository;
    private final WorkoutRepository workoutRepository;

    @Transactional
    public RoutineResponse createRoutine(String workosId, CreateRoutineRequest request) {
        User user = userRepository.findByWorkosIdForUpdate(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UUID userId = user.getId();
        log.info("Creating routine for user (userId={}, name={})", userId, request.getName());

        if (routineRepository.existsByUserIdAndNameIgnoreCase(userId, request.getName())) {
            throw new ConflictException("Routine name already exists");
        }
        lockWorkoutReferences(userId, request.getPattern());
        LocalDateTime now = LocalDateTime.now();
        boolean requestedActive = Boolean.TRUE.equals(request.getActive());
        if (requestedActive) {
            requireVersion(request.getProfileBaseVersion(), user.getProfileVersion(), "Profile");
        }
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
            user.setProfileVersion(user.getProfileVersion() + 1);
            userRepository.save(user);
            log.info("Marked new routine as active for user (userId={}, routineId={})", userId, saved.getId());
        }

        routineRepository.flush();
        userRepository.flush();
        List<RoutinePattern> patterns = routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(saved.getId());
        log.info("Created routine successfully (userId={}, routineId={}, patternDays={}, active={})",
                userId, saved.getId(), patterns.size(), requestedActive);
        return RoutineResponse.from(saved, patterns, requestedActive ? user.getProfileVersion() : null);
    }

    public List<RoutineResponse> listRoutines(String workosId) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        log.info("Listing routines for user (userId={})", user.getId());
        List<Routine> routines = routineRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId());
        List<RoutineResponse> responses = routines.stream()
                .map(r -> RoutineResponse.from(r, routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(r.getId())))
                .collect(Collectors.toList());
        log.info("Listed routines for user (userId={}, routineCount={})", user.getId(), responses.size());
        return responses;
    }

    public RoutineResponse getRoutine(String workosId, String id) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        log.info("Fetching routine for user (userId={}, routineId={})", user.getId(), id);
        Routine routine = routineRepository.findByIdAndUserId(UUID.fromString(id), user.getId())
                .orElseThrow(() -> new NotFoundException("Routine not found"));
        List<RoutinePattern> patterns = routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(routine.getId());
        log.info("Fetched routine for user (userId={}, routineId={}, patternDays={})", user.getId(), routine.getId(), patterns.size());
        return RoutineResponse.from(routine, patterns);
    }

    public RoutineResponse getActiveRoutine(String workosId) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        log.info("Fetching active routine for user (userId={})", user.getId());
        UUID activeId = user.getActiveRoutineId();
        if (activeId == null) {
            throw new NotFoundException("No active routine");
        }
        Routine routine = routineRepository.findByIdAndUserId(activeId, user.getId())
                .orElseThrow(() -> new NotFoundException("Active routine not found"));
        List<RoutinePattern> patterns = routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(routine.getId());
        log.info("Fetched active routine for user (userId={}, routineId={}, patternDays={})", user.getId(), routine.getId(), patterns.size());
        return RoutineResponse.from(routine, patterns);
    }

    @Transactional
    public RoutineResponse updateRoutine(String workosId, String id, UpdateRoutineRequest request) {
        User user = userRepository.findByWorkosIdForUpdate(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UUID userId = user.getId();
        log.info("Updating routine for user (userId={}, routineId={})", userId, id);

        UUID routineId = UUID.fromString(id);
        Routine probe = routineRepository.findById(routineId)
                .orElseThrow(() -> new NotFoundException("Routine not found"));
        if (!userId.equals(probe.getUserId())) {
            log.warn("Routine update denied: routine does not belong to user (userId={}, routineId={})", userId, routineId);
            throw new UnauthorizedException("Not authorized to update this routine");
        }
        if (request.getPattern() != null && request.getPattern().isEmpty()) {
            throw new BadRequestException("Pattern is required");
        }
        lockWorkoutReferences(userId, request.getPattern());
        Routine existing = routineRepository.findByIdForUpdate(routineId)
                .orElseThrow(() -> new NotFoundException("Routine not found"));

        requireVersion(request.getBaseVersion(), existing.getVersion(), "Routine");
        requireVersion(request.getProfileBaseVersion(), user.getProfileVersion(), "Profile");

        boolean routineMutation = request.getName() != null
                || request.getDescription() != null
                || request.getPattern() != null
                || request.getStartDate() != null
                || request.getRoutineType() != null;
        boolean profileMutation = changesActivePointer(user, existing, request.getActive());
        requireCompletePreconditions(
                routineMutation,
                profileMutation,
                request.getBaseVersion(),
                request.getProfileBaseVersion());
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
            } else {
                if (existing.getId().equals(user.getActiveRoutineId())) {
                    user.setActiveRoutineId(null);
                }
            }
            if (profileMutation) {
                user.setProfileVersion(user.getProfileVersion() + 1);
                userRepository.save(user);
            }
            log.info("Updated active routine assignment for user (userId={}, routineId={}, active={})",
                    userId, existing.getId(), request.getActive());
        }
        if (changed) {
            existing.setUpdatedAt(LocalDateTime.now());
            routineRepository.save(existing);
        }
        routineRepository.flush();
        userRepository.flush();
        List<RoutinePattern> patterns = routinePatternRepository.findAllByRoutineIdOrderByDayIndexAsc(routineId);
        log.info("Completed routine update (userId={}, routineId={}, changed={}, patternDays={})",
                userId, routineId, changed, patterns.size());
        return RoutineResponse.from(existing, patterns, request.getActive() != null ? user.getProfileVersion() : null);
    }

    private void lockWorkoutReferences(UUID userId, List<RoutinePatternRequest> pattern) {
        if (pattern == null) {
            return;
        }
        Set<UUID> workoutIds = pattern.stream()
                .map(RoutinePatternRequest::getWorkoutId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!workoutIds.isEmpty()
                && workoutRepository.findAllByIdInAndUserIdForUpdate(workoutIds, userId).size() != workoutIds.size()) {
            throw new NotFoundException("Workout not found");
        }
    }

    @Transactional
    public RoutineDeleteAck deleteRoutine(String workosId, String id, Long baseVersion, Long profileBaseVersion) {
        User user = userRepository.findByWorkosIdForUpdate(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UUID userId = user.getId();
        log.info("Deleting routine for user (userId={}, routineId={})", userId, id);

        UUID routineId = UUID.fromString(id);
        Routine probe = routineRepository.findById(routineId)
                .orElseThrow(() -> new NotFoundException("Routine not found"));
        if (!userId.equals(probe.getUserId())) {
            log.warn("Routine deletion denied: routine does not belong to user (userId={}, routineId={})", userId, routineId);
            throw new UnauthorizedException("Not authorized to delete this routine");
        }
        Routine existing = routineRepository.findByIdForUpdate(routineId)
                .orElseThrow(() -> new NotFoundException("Routine not found"));
        boolean clearsActive = routineId.equals(user.getActiveRoutineId());
        requireCompletePreconditions(true, clearsActive, baseVersion, profileBaseVersion);
        requireVersion(baseVersion, existing.getVersion(), "Routine");
        if (clearsActive) {
            requireVersion(profileBaseVersion, user.getProfileVersion(), "Profile");
            user.setActiveRoutineId(null);
            user.setProfileVersion(user.getProfileVersion() + 1);
            userRepository.save(user);
        }
        routinePatternRepository.deleteByRoutineId(routineId);
        routineRepository.deleteById(routineId);
        routineRepository.flush();
        userRepository.flush();
        log.info("Deleted routine successfully (userId={}, routineId={})", userId, routineId);
        return RoutineDeleteAck.builder()
                .deletedRoutineId(routineId)
                .profileVersion(clearsActive ? user.getProfileVersion() : null)
                .build();
    }

    private boolean changesActivePointer(User user, Routine routine, Boolean active) {
        if (active == null) {
            return false;
        }
        return active ? !routine.getId().equals(user.getActiveRoutineId())
                : routine.getId().equals(user.getActiveRoutineId());
    }

    private void requireCompletePreconditions(
            boolean routineMutation,
            boolean profileMutation,
            Long baseVersion,
            Long profileBaseVersion) {
        boolean versioned = baseVersion != null || profileBaseVersion != null;
        if (!versioned) {
            return;
        }
        if ((routineMutation && baseVersion == null) || (profileMutation && profileBaseVersion == null)) {
            throw new BadRequestException("Versioned request must include every affected root version");
        }
    }

    private void requireVersion(Long supplied, long current, String entityName) {
        if (supplied != null && supplied.longValue() != current) {
            throw new StaleVersionException(entityName + " changed since it was downloaded", current);
        }
    }

    private void requireVersion(Long supplied, Long current, String entityName) {
        if (supplied != null && !supplied.equals(current)) {
            throw new StaleVersionException(entityName + " changed since it was downloaded", current);
        }
    }
}
