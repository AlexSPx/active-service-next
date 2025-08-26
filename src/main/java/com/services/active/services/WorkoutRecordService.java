package com.services.active.services;

import com.services.active.dto.UserWorkoutRecordsResponse;
import com.services.active.dto.WorkoutRecordRequest;
import com.services.active.exceptions.NotFoundException;
import com.services.active.models.ExerciseRecord;
import com.services.active.models.Workout;
import com.services.active.models.WorkoutRecord;
import com.services.active.repository.ExerciseRecordRepository;
import com.services.active.repository.ExerciseRepository;
import com.services.active.repository.WorkoutRecordRepository;
import com.services.active.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkoutRecordService {

    private final WorkoutRepository workoutRepository;
    private final ExerciseRepository exerciseRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final WorkoutRecordRepository workoutRecordRepository;

    public String createWorkoutRecord(String userId, WorkoutRecordRequest request) {
        log.info("createWorkoutRecord userId: {}, workoutId: {}", userId, request.getWorkoutId());

        // Fetch the workout to get the title for snapshot
        Workout workout = workoutRepository.findById(request.getWorkoutId())
                .orElseThrow(() -> new NotFoundException("Workout not found: " + request.getWorkoutId()));

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

        List<String> exerciseRecordIds = exerciseRecordRepository.saveAllAndReturnIds(exerciseRecords);

        WorkoutRecord workoutRecord = WorkoutRecord.builder()
                .userId(userId)
                .workoutId(request.getWorkoutId())
                .workoutTitle(workout.getTitle())
                .notes(request.getNotes())
                .exerciseRecordIds(exerciseRecordIds)
                .startTime(request.getStartTime())
                .createdAt(LocalDateTime.now())
                .build();

        WorkoutRecord saved = workoutRecordRepository.save(workoutRecord);
        workoutRepository.updateWorkoutRecordIds(request.getWorkoutId(), saved.getId());
        return saved.getId();
    }

    public List<UserWorkoutRecordsResponse> getWorkoutRecords(String userId) {
        return workoutRecordRepository.findAllByUserId(userId)
                .stream()
                .map(workoutRecord -> {
                    var exerciseRecords = exerciseRecordRepository.findAllById(workoutRecord.getExerciseRecordIds())
                            .stream()
                            .map(exRecord -> {
                                var exercise = exerciseRepository.findById(exRecord.getExerciseId())
                                        .orElse(null);
                                String exerciseName = exercise != null ? exercise.getName() : "Unknown";
                                return UserWorkoutRecordsResponse.ExerciseRecordResponse.builder()
                                        .exerciseName(exerciseName)
                                        .reps(exRecord.getReps())
                                        .weight(exRecord.getWeight())
                                        .durationSeconds(exRecord.getDurationSeconds())
                                        .notes(exRecord.getNotes())
                                        .build();
                            })
                            .collect(Collectors.toList());

                    return UserWorkoutRecordsResponse.builder()
                            .id(workoutRecord.getId())
                            .workoutTitle(workoutRecord.getWorkoutTitle())
                            .startTime(workoutRecord.getStartTime())
                            .workoutId(workoutRecord.getWorkoutId())
                            .notes(workoutRecord.getNotes())
                            .exerciseRecords(exerciseRecords)
                            .startTime(workoutRecord.getStartTime())
                            .createdAt(workoutRecord.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

}
