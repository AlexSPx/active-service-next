package com.services.active.services;

import com.services.active.dto.CreateWorkoutRequest;
import com.services.active.dto.WorkoutTemplateResponse;
import com.services.active.dto.TemplateExerciseResponse;
import com.services.active.dto.UserWorkoutResponse;
import com.services.active.dto.WorkoutDeleteAck;
import com.services.active.exceptions.BadRequestException;
import com.services.active.exceptions.NotFoundException;
import com.services.active.exceptions.StaleVersionException;
import com.services.active.exceptions.UnauthorizedException;
import com.services.active.models.Workout;
import com.services.active.models.WorkoutTemplate;
import com.services.active.models.TemplateExercise;
import com.services.active.models.Exercise;
import com.services.active.models.user.User;
import com.services.active.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkoutService {
    private final UserRepository userRepository;
    private final WorkoutRepository workoutRepository;
    private final WorkoutTemplateRepository workoutTemplateRepository;
    private final TemplateExerciseRepository templateExerciseRepository;
    private final ExerciseRepository exerciseRepository;
    private final RoutineRepository routineRepository;
    private final RoutinePatternRepository routinePatternRepository;

    @Transactional
    public Workout createWorkout(String workosId, CreateWorkoutRequest request) {
        User user = userRepository.findByWorkosIdForUpdate(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UUID userId = user.getId();
        log.info("Creating workout for user (userId={}, title={})", userId, request.getTitle());

        if (request.getTemplate() == null) {
            throw new BadRequestException("Template is required");
        }

        WorkoutTemplate workoutTemplate = WorkoutTemplate.builder()
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        WorkoutTemplate savedTemplate = workoutTemplateRepository.save(workoutTemplate);

        // Save template exercises
        if (request.getTemplate().getExercises() != null) {
            for (int i = 0; i < request.getTemplate().getExercises().size(); i++) {
                var te = request.getTemplate().getExercises().get(i);
                TemplateExercise entity = TemplateExercise.builder()
                        .templateId(savedTemplate.getId())
                        .exerciseId(te.getExerciseId())
                        .ordinal(i)
                        .reps(te.getReps())
                        .weight(te.getWeight())
                        .durationSeconds(te.getDurationSeconds())
                        .notes(te.getNotes())
                        .build();
                templateExerciseRepository.save(entity);
            }
        }

        Workout workout = Workout.builder()
                .userId(userId)
                .title(request.getTitle())
                .notes(request.getNotes())
                .templateId(savedTemplate.getId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Workout savedWorkout = workoutRepository.saveAndFlush(workout);
        int templateExerciseCount = request.getTemplate().getExercises() != null ? request.getTemplate().getExercises().size() : 0;
        log.info("Created workout successfully (userId={}, workoutId={}, templateId={}, templateExercises={})",
                userId, savedWorkout.getId(), savedTemplate.getId(), templateExerciseCount);
        return savedWorkout;
    }

    public List<UserWorkoutResponse> getUserWorkouts(String workosId) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UUID userId = user.getId();
        log.info("Fetching workouts for user (userId={})", userId);

        List<Workout> workouts = workoutRepository.findAllByUserId(userId);
        List<UserWorkoutResponse> result = new ArrayList<>();
        for (Workout workout : workouts) {
            WorkoutTemplate template = workoutTemplateRepository.findById(workout.getTemplateId())
                    .orElseThrow(() -> new NotFoundException("Template not found for workout: " + workout.getId()));

            List<TemplateExercise> templateExercises = templateExerciseRepository.findAllByTemplateIdOrderByOrdinalAsc(template.getId());
            WorkoutTemplateResponse templateResponse = buildTemplateResponse(template, templateExercises);
            result.add(UserWorkoutResponse.from(workout, templateResponse));
        }
        log.info("Fetched workouts for user (userId={}, workoutCount={})", userId, result.size());
        return result;
    }

    @Transactional
    public Workout updateWorkout(String workosId, String workoutId, com.services.active.dto.UpdateWorkoutRequest request) {
        User user = userRepository.findByWorkosIdForUpdate(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UUID userId = user.getId();
        log.info("Updating workout for user (userId={}, workoutId={})", userId, workoutId);

        UUID wId = UUID.fromString(workoutId);
        Workout probe = workoutRepository.findById(wId)
                .orElseThrow(() -> new NotFoundException("Workout not found"));

        if (!userId.equals(probe.getUserId())) {
            log.warn("Workout update denied: workout does not belong to user (userId={}, workoutId={})", userId, wId);
            throw new UnauthorizedException("Not authorized to update this workout");
        }
        Workout workout = workoutRepository.findByIdForUpdate(wId)
                .orElseThrow(() -> new NotFoundException("Workout not found"));
        requireVersion(request.getBaseVersion(), workout.getVersion(), "Workout");

        boolean workoutChanged = false;
        if (request.getTitle() != null) {
            workout.setTitle(request.getTitle());
            workoutChanged = true;
        }
        if (request.getNotes() != null) {
            workout.setNotes(request.getNotes());
            workoutChanged = true;
        }
        if (workoutChanged) {
            workout.setUpdatedAt(LocalDateTime.now());
        }

        boolean templateChanged = request.getTemplate() != null && request.getTemplate().getExercises() != null
                && !request.getTemplate().getExercises().isEmpty();
        if (templateChanged
                && !request.getTemplate().getExercises().isEmpty()) {
            WorkoutTemplate template = workoutTemplateRepository.findById(workout.getTemplateId())
                    .orElseThrow(() -> new NotFoundException("Template not found for workout: " + workoutId));
            template.setUpdatedAt(LocalDateTime.now());
            workoutTemplateRepository.save(template);

            // Replace template exercises
            templateExerciseRepository.deleteByTemplateId(template.getId());
            for (int i = 0; i < request.getTemplate().getExercises().size(); i++) {
                var te = request.getTemplate().getExercises().get(i);
                TemplateExercise entity = TemplateExercise.builder()
                        .templateId(template.getId())
                        .exerciseId(te.getExerciseId())
                        .ordinal(i)
                        .reps(te.getReps())
                        .weight(te.getWeight())
                        .durationSeconds(te.getDurationSeconds())
                        .notes(te.getNotes())
                        .build();
                templateExerciseRepository.save(entity);
            }
            log.info("Replaced workout template exercises (workoutId={}, templateId={}, exerciseCount={})",
                    workout.getId(), template.getId(), request.getTemplate().getExercises().size());
        }

        if (templateChanged) {
            workout.setUpdatedAt(LocalDateTime.now());
        }
        Workout updatedWorkout = workoutChanged || templateChanged ? workoutRepository.saveAndFlush(workout) : workout;
        log.info("Completed workout update (userId={}, workoutId={}, workoutMetadataChanged={})",
                userId, updatedWorkout.getId(), workoutChanged);
        return updatedWorkout;
    }

    @Transactional
    public WorkoutDeleteAck deleteWorkout(String workosId, String workoutId, Long baseVersion) {
        User user = userRepository.findByWorkosIdForUpdate(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UUID userId = user.getId();
        log.info("Deleting workout for user (userId={}, workoutId={})", userId, workoutId);

        UUID wId = UUID.fromString(workoutId);
        Workout probe = workoutRepository.findById(wId)
                .orElseThrow(() -> new NotFoundException("Workout not found"));
        if (!userId.equals(probe.getUserId())) {
            log.warn("Workout deletion denied: workout does not belong to user (userId={}, workoutId={})", userId, wId);
            throw new UnauthorizedException("Not authorized to delete this workout");
        }
        Workout workout = workoutRepository.findByIdForUpdate(wId)
                .orElseThrow(() -> new NotFoundException("Workout not found"));
        requireVersion(baseVersion, workout.getVersion(), "Workout");

        List<UUID> affectedRoutineIds = routinePatternRepository.findRoutineIdsByWorkoutId(wId);
        List<com.services.active.models.Routine> affectedRoutines = affectedRoutineIds.isEmpty()
                ? List.of()
                : routineRepository.findAllByIdInAndUserIdForUpdate(affectedRoutineIds, userId);
        LocalDateTime now = LocalDateTime.now();
        affectedRoutines.forEach(routine -> routine.setUpdatedAt(now));
        routineRepository.saveAllAndFlush(affectedRoutines);

        UUID templateId = workout.getTemplateId();
        workoutRepository.deleteById(wId);
        if (templateId != null) {
            templateExerciseRepository.deleteByTemplateId(templateId);
            workoutTemplateRepository.deleteById(templateId);
        }
        workoutRepository.flush();
        log.info("Deleted workout successfully (userId={}, workoutId={}, deletedTemplate={})", userId, wId, templateId != null);
        return WorkoutDeleteAck.builder()
                .deletedWorkoutId(wId)
                .routineVersions(affectedRoutines.stream()
                        .map(routine -> new WorkoutDeleteAck.EntityVersion(routine.getId(), routine.getVersion()))
                        .toList())
                .build();
    }

    private void requireVersion(Long supplied, Long current, String entityName) {
        if (supplied != null && !supplied.equals(current)) {
            throw new StaleVersionException(entityName + " changed since it was downloaded", current);
        }
    }

    private WorkoutTemplateResponse buildTemplateResponse(WorkoutTemplate template, List<TemplateExercise> templateExercises) {
        if (template == null) return null;
        List<TemplateExerciseResponse> exerciseResponses = new ArrayList<>();
        if (templateExercises != null && !templateExercises.isEmpty()) {
            Set<UUID> ids = templateExercises.stream()
                    .map(TemplateExercise::getExerciseId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());
            Map<UUID, Exercise> byId = exerciseRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(Exercise::getId, e -> e));
            for (TemplateExercise te : templateExercises) {
                Exercise ex = te.getExerciseId() == null ? null : byId.get(te.getExerciseId());
                exerciseResponses.add(TemplateExerciseResponse.builder()
                        .exerciseId(te.getExerciseId() != null ? te.getExerciseId().toString() : null)
                        .exerciseTitle(ex != null ? ex.getName() : null)
                        .reps(te.getReps())
                        .weight(te.getWeight())
                        .durationSeconds(te.getDurationSeconds())
                        .notes(te.getNotes())
                        .category(ex != null ? ex.getCategory() : null)
                        .primaryMuscles(ex != null ? ex.getPrimaryMuscles() : null)
                        .secondaryMuscles(ex != null ? ex.getSecondaryMuscles() : null)
                        .build());}
        }
        return WorkoutTemplateResponse.builder()
                .id(template.getId().toString())
                .exercises(exerciseResponses)
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
