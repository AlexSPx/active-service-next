package com.services.active.services;

import com.services.active.dto.CreateWorkoutRequest;
import com.services.active.dto.UserWorkoutRecordsResponse;
import com.services.active.dto.WorkoutRecordRequest;
import com.services.active.dto.WorkoutWithTemplate;
import com.services.active.models.ExerciseRecord;
import com.services.active.models.Workout;
import com.services.active.models.WorkoutRecord;
import com.services.active.models.WorkoutTemplate;
import com.services.active.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkoutService {
    private final WorkoutRepository workoutRepository;
    private final WorkoutTemplateRepository workoutTemplateRepository;

    private final ExerciseRepository exerciseRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final WorkoutRecordRepository workoutRecordRepository;

    public Mono<Workout> createWorkout(String userId, CreateWorkoutRequest request) {
        WorkoutTemplate workoutTemplate = WorkoutTemplate.builder()
                .exercises(request.getTemplate().getExercises())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return workoutTemplateRepository.save(workoutTemplate)
                .flatMap(savedTemplate -> {
                    Workout workout = Workout.builder()
                            .userId(userId)
                            .notes(request.getNotes())
                            .workoutRecordIds(new ArrayList<>())
                            .templateId(savedTemplate.getId())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

                    return workoutRepository.save(workout);
                });
    }


    public Flux<WorkoutWithTemplate> getUserWorkouts(String userId) {
        return workoutRepository.findAllByUserId(userId)
                .flatMap(workout -> {
                    Mono<WorkoutTemplate> templateMono = workoutTemplateRepository.findById(workout.getTemplateId());

                    return templateMono.map(template -> WorkoutWithTemplate.createFromWorkoutAndTemplate(workout, template)
                    ).switchIfEmpty(Mono.error(new RuntimeException("Template not found for workout: " + workout.getId())));
                });
    }

    public Mono<String> createWorkoutRecord(String userId, WorkoutRecordRequest request) {
        log.info("createWorkoutRecord userId: {}, workoutId: {}", userId, request.getWorkoutId());

        List<ExerciseRecord> exerciseRecords = request.getExerciseRecords().stream()
                .map(exercise -> ExerciseRecord.builder()
                        .exerciseId(exercise.getExerciseId())
                        .reps(exercise.getReps())
                        .weight(exercise.getWeight())
                        .durationSeconds(exercise.getDurationSeconds())
                        .notes(exercise.getNotes())
                        .userId(userId)
                        .createdAt(LocalDateTime.now())
                        .build())
                .toList();

        return exerciseRecordRepository.saveAllAndReturnIds(exerciseRecords)
                .collectList()
                .flatMap(exerciseRecordIds -> {
                    WorkoutRecord workoutRecord = WorkoutRecord.builder()
                            .userId(userId)
                            .workoutId(request.getWorkoutId())
                            .notes(request.getNotes())
                            .exerciseRecordIds(exerciseRecordIds)
                            .createdAt(LocalDateTime.now())
                            .build();

                    return workoutRecordRepository.save(workoutRecord);
                })
                .flatMap(record ->
                        workoutRepository.updateWorkoutRecordIds(request.getWorkoutId(), record.getId())
                                .then(Mono.just(record.getId()))
                );
    }

    public Flux<UserWorkoutRecordsResponse> getWorkoutRecords(String userId) {
        return workoutRecordRepository.findAllByUserId(userId)
                .flatMap(workoutRecord ->
                    exerciseRecordRepository.findAllById(workoutRecord.getExerciseRecordIds())
                            .flatMap(exRecord ->
                                    exerciseRepository.findById(exRecord.getExerciseId())
                                            .map(exercise -> UserWorkoutRecordsResponse.ExerciseRecordResponse.builder()
                                                    .exerciseName(exercise.getName())
                                                    .reps(exRecord.getReps())
                                                    .weight(exRecord.getWeight())
                                                    .durationSeconds(exRecord.getDurationSeconds())
                                                    .notes(exRecord.getNotes())
                                                    .build())
                            )

                            .collectList()
                            .map(exerciseRecords -> UserWorkoutRecordsResponse.builder()
                                    .workoutId(workoutRecord.getWorkoutId())
                                    .notes(workoutRecord.getNotes())
                                    .exerciseRecords(exerciseRecords)
                                    .createdAt(workoutRecord.getCreatedAt())
                                    .build())
                );
    }
}
