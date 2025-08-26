package com.services.active.services;

import com.services.active.dto.UserWorkoutRecordsResponse;
import com.services.active.dto.WorkoutRecordRequest;
import com.services.active.exceptions.NotFoundException;
import com.services.active.domain.AchievementCalculator;
import com.services.active.models.ExercisePersonalBest;
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
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkoutRecordService {

    private final WorkoutRepository workoutRepository;
    private final ExerciseRepository exerciseRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final PersonalBestService personalBestService;

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

        // Load current PBs for all exerciseIds involved, once
        Set<String> exerciseIds = exerciseRecords.stream().map(ExerciseRecord::getExerciseId).collect(Collectors.toSet());
        Map<String, ExercisePersonalBest> currentPbByExercise = personalBestService.getCurrentPbs(userId, exerciseIds);

        // Compute achievements per record using progressive PBs (within this batch)
        for (ExerciseRecord record : exerciseRecords) {
            String exId = record.getExerciseId();
            ExercisePersonalBest currentPb = currentPbByExercise.get(exId);

            List<Integer> reps = record.getReps();
            List<Double> weight = record.getWeight();
            boolean hasStrength = reps != null && weight != null && !reps.isEmpty() && !weight.isEmpty();
            if (!hasStrength) continue;

            var bestOneRmResult = AchievementCalculator.computeBestEstimatedOneRm(reps, weight);
            Double bestOneRm = bestOneRmResult.bestOneRm();
            Integer bestSetIdx = bestOneRmResult.bestSetIndex();

            Double previousOneRm = (currentPb != null) ? currentPb.getOneRm() : null;
            if (bestOneRm != null && (previousOneRm == null || bestOneRm > previousOneRm)) {
                record.setAchievedOneRm(ExerciseRecord.OneRmAchievement.builder()
                        .value(bestOneRm)
                        .setIndex(bestSetIdx)
                        .build());
                if (currentPb == null) currentPb = ExercisePersonalBest.builder().userId(userId).exerciseId(exId).build();
                currentPb.setOneRm(bestOneRm);
                currentPbByExercise.put(exId, currentPb);
            }

            Double totalVolume = AchievementCalculator.computeTotalVolume(reps, weight);
            Double previousVolume = (currentPb != null) ? currentPb.getTotalVolume() : null;
            if (totalVolume != null && (previousVolume == null || totalVolume > previousVolume)) {
                record.setAchievedTotalVolume(ExerciseRecord.TotalVolumeAchievement.builder()
                        .value(totalVolume)
                        .build());
                if (currentPb == null) currentPb = ExercisePersonalBest.builder().userId(userId).exerciseId(exId).build();
                currentPb.setTotalVolume(totalVolume);
                currentPbByExercise.put(exId, currentPb);
            }
        }

        List<String> exerciseRecordIds = exerciseRecordRepository.saveAllAndReturnIds(exerciseRecords);

        // Persist PB documents for records that achieved PRs
        personalBestService.persistPrs(userId, exerciseRecords, exerciseRecordIds);

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
                    var exRecords = exerciseRecordRepository.findAllById(workoutRecord.getExerciseRecordIds());

                    // Batch-load all exercises used in this workout record to avoid N+1
                    Set<String> exIds = exRecords.stream().map(ExerciseRecord::getExerciseId).collect(Collectors.toSet());
                    Map<String, String> exerciseNameById = new HashMap<>();
                    exerciseRepository.findAllById(exIds).forEach(ex -> exerciseNameById.put(ex.getId(), ex.getName()));

                    var exerciseResponses = exRecords.stream()
                            .map(exRecord -> {
                                String exerciseName = exerciseNameById.getOrDefault(exRecord.getExerciseId(), "Unknown");
                                return UserWorkoutRecordsResponse.ExerciseRecordResponse.builder()
                                        .exerciseName(exerciseName)
                                        .reps(exRecord.getReps())
                                        .weight(exRecord.getWeight())
                                        .durationSeconds(exRecord.getDurationSeconds())
                                        .notes(exRecord.getNotes())
                                        .achievedOneRmValue(exRecord.getAchievedOneRm() != null ? exRecord.getAchievedOneRm().getValue() : null)
                                        .achievedOneRmSetIndex(exRecord.getAchievedOneRm() != null ? exRecord.getAchievedOneRm().getSetIndex() : null)
                                        .achievedTotalVolumeValue(exRecord.getAchievedTotalVolume() != null ? exRecord.getAchievedTotalVolume().getValue() : null)
                                        .build();
                            })
                            .collect(Collectors.toList());

                    return UserWorkoutRecordsResponse.builder()
                            .id(workoutRecord.getId())
                            .workoutTitle(workoutRecord.getWorkoutTitle())
                            .startTime(workoutRecord.getStartTime())
                            .workoutId(workoutRecord.getWorkoutId())
                            .notes(workoutRecord.getNotes())
                            .exerciseRecords(exerciseResponses)
                            .startTime(workoutRecord.getStartTime())
                            .createdAt(workoutRecord.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }
}
