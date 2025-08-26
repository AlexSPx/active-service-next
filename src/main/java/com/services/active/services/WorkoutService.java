package com.services.active.services;

import com.services.active.dto.CreateWorkoutRequest;
import com.services.active.dto.WorkoutWithTemplate;
import com.services.active.exceptions.BadRequestException;
import com.services.active.exceptions.NotFoundException;
import com.services.active.models.Workout;
import com.services.active.models.WorkoutTemplate;
import com.services.active.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkoutService {
    private final WorkoutRepository workoutRepository;
    private final WorkoutTemplateRepository workoutTemplateRepository;

    public Workout createWorkout(String userId, CreateWorkoutRequest request) {
        if (request.getTemplate() == null) {
            throw new BadRequestException("Template is required");
        }

        WorkoutTemplate workoutTemplate = WorkoutTemplate.builder()
                .exercises(request.getTemplate().getExercises())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        WorkoutTemplate savedTemplate = workoutTemplateRepository.save(workoutTemplate);

        Workout workout = Workout.builder()
                .userId(userId)
                .title(request.getTitle())
                .notes(request.getNotes())
                .workoutRecordIds(new ArrayList<>())
                .templateId(savedTemplate.getId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return workoutRepository.save(workout);
    }

    public List<WorkoutWithTemplate> getUserWorkouts(String userId) {
        List<Workout> workouts = workoutRepository.findAllByUserId(userId);
        List<WorkoutWithTemplate> result = new ArrayList<>();
        for (Workout workout : workouts) {
            WorkoutTemplate template = workoutTemplateRepository.findById(workout.getTemplateId())
                    .orElseThrow(() -> new NotFoundException("Template not found for workout: " + workout.getId()));
            result.add(WorkoutWithTemplate.createFromWorkoutAndTemplate(workout, template));
        }
        return result;
    }

    public Workout updateWorkout(String userId, String workoutId, com.services.active.dto.UpdateWorkoutRequest request) {
        Workout workout = workoutRepository.findById(workoutId)
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
            template.setExercises(request.getTemplate().getExercises());
            template.setUpdatedAt(LocalDateTime.now());
            workoutTemplateRepository.save(template);
        }

        return workoutChanged ? workoutRepository.save(workout) : workout;
    }

    public void deleteWorkout(String userId, String workoutId) {
        Workout workout = workoutRepository.findById(workoutId)
                .orElseThrow(() -> new NotFoundException("Workout not found"));
        if (!userId.equals(workout.getUserId())) {
            throw new com.services.active.exceptions.UnauthorizedException("Not authorized to delete this workout");
        }
        // Delete the workout and its template; keep records
        String templateId = workout.getTemplateId();
        workoutRepository.deleteById(workoutId);
        if (templateId != null) {
            workoutTemplateRepository.deleteById(templateId);
        }
    }
}
