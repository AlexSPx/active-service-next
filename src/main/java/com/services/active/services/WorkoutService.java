package com.services.active.services;

import com.services.active.dto.CreateWorkoutRequest;
import com.services.active.dto.CreateWorkoutTemplateRequest;
import com.services.active.dto.WorkoutTemplateResponse;
import com.services.active.dto.TemplateExerciseResponse;
import com.services.active.dto.UserWorkoutResponse;
import com.services.active.exceptions.BadRequestException;
import com.services.active.exceptions.NotFoundException;
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

    @Transactional
    public Workout createWorkout(String workosId, CreateWorkoutRequest request) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UUID userId = user.getId();

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

        return workoutRepository.save(workout);
    }

    public List<UserWorkoutResponse> getUserWorkouts(String workosId) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UUID userId = user.getId();

        List<Workout> workouts = workoutRepository.findAllByUserId(userId);
        List<UserWorkoutResponse> result = new ArrayList<>();
        for (Workout workout : workouts) {
            WorkoutTemplate template = workoutTemplateRepository.findById(workout.getTemplateId())
                    .orElseThrow(() -> new NotFoundException("Template not found for workout: " + workout.getId()));

            List<TemplateExercise> templateExercises = templateExerciseRepository.findAllByTemplateIdOrderByOrdinalAsc(template.getId());
            WorkoutTemplateResponse templateResponse = buildTemplateResponse(template, templateExercises);
            result.add(UserWorkoutResponse.from(workout, templateResponse));
        }
        return result;
    }

    @Transactional
    public Workout updateWorkout(String workosId, String workoutId, com.services.active.dto.UpdateWorkoutRequest request) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UUID userId = user.getId();

        UUID wId = UUID.fromString(workoutId);
        Workout workout = workoutRepository.findById(wId)
                .orElseThrow(() -> new NotFoundException("Workout not found"));

        if (!userId.equals(workout.getUserId())) {
            throw new com.services.active.exceptions.UnauthorizedException("Not authorized to update this workout");
        }

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

        if (request.getTemplate() != null && request.getTemplate().getExercises() != null
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
        }

        return workoutChanged ? workoutRepository.save(workout) : workout;
    }

    @Transactional
    public void deleteWorkout(String workosId, String workoutId) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UUID userId = user.getId();

        UUID wId = UUID.fromString(workoutId);
        Workout workout = workoutRepository.findById(wId)
                .orElseThrow(() -> new NotFoundException("Workout not found"));
        if (!userId.equals(workout.getUserId())) {
            throw new com.services.active.exceptions.UnauthorizedException("Not authorized to delete this workout");
        }
        UUID templateId = workout.getTemplateId();
        workoutRepository.deleteById(wId);
        if (templateId != null) {
            templateExerciseRepository.deleteByTemplateId(templateId);
            workoutTemplateRepository.deleteById(templateId);
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
