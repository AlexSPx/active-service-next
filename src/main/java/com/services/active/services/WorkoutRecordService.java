package com.services.active.services;

import com.services.active.dto.UserWorkoutRecordsResponse;
import com.services.active.dto.WorkoutRecordRequest;
import com.services.active.exceptions.NotFoundException;
import com.services.active.exceptions.UnauthorizedException;
import com.services.active.domain.AchievementCalculator;
import com.services.active.models.ExercisePersonalBest;
import com.services.active.models.ExerciseRecord;
import com.services.active.models.Workout;
import com.services.active.models.WorkoutRecord;
import com.services.active.models.user.User;
import com.services.active.repository.ExerciseRecordRepository;
import com.services.active.repository.ExerciseRepository;
import com.services.active.repository.UserRepository;
import com.services.active.repository.WorkoutRecordRepository;
import com.services.active.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkoutRecordService {

    private final UserRepository userRepository;
    private final WorkoutRepository workoutRepository;
    private final ExerciseRepository exerciseRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final PersonalBestService personalBestService;
    private final StreakService streakService;

    @Transactional
    public com.services.active.dto.WorkoutRecordCreateResponse createWorkoutRecord(String workosId, WorkoutRecordRequest request) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UUID userId = user.getId();

        log.info("createWorkoutRecord workosId: {}, userId: {}, workoutId: {}", workosId, userId, request.getWorkoutId());

        // Fetch the workout to get the title for snapshot
        UUID workoutId = UUID.fromString(request.getWorkoutId());
        Workout workout = workoutRepository.findById(workoutId)
                .orElseThrow(() -> new NotFoundException("Workout not found: " + request.getWorkoutId()));

        // Save the workout record first so we have an ID for exercise records
        WorkoutRecord workoutRecord = WorkoutRecord.builder()
                .userId(userId)
                .workoutId(workoutId)
                .workoutTitle(workout.getTitle())
                .notes(request.getNotes())
                .startTime(request.getStartTime())
                .createdAt(LocalDateTime.now())
                .build();
        WorkoutRecord saved = workoutRecordRepository.save(workoutRecord);

        List<ExerciseRecord> exerciseRecords = new ArrayList<>();
        for (int i = 0; i < request.getExerciseRecords().size(); i++) {
            var exercise = request.getExerciseRecords().get(i);
            exerciseRecords.add(ExerciseRecord.builder()
                    .exerciseId(UUID.fromString(exercise.getExerciseId()))
                    .workoutRecordId(saved.getId())
                    .reps(exercise.getReps())
                    .weight(exercise.getWeight())
                    .durationSeconds(exercise.getDurationSeconds())
                    .notes(exercise.getNotes())
                    .userId(userId)
                    .ordinal(i)
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        // Load current PBs for all exerciseIds involved, once
        Set<UUID> exerciseIds = exerciseRecords.stream().map(ExerciseRecord::getExerciseId).collect(Collectors.toSet());
        Map<UUID, ExercisePersonalBest> currentPbByExercise = personalBestService.getCurrentPbs(userId, exerciseIds);

        // Compute achievements per record using progressive PBs (within this batch)
        for (ExerciseRecord record : exerciseRecords) {
            UUID exId = record.getExerciseId();
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
                record.setAchievedOneRmValue(bestOneRm);
                record.setAchievedOneRmSetIndex(bestSetIdx);
                if (currentPb == null) currentPb = ExercisePersonalBest.builder().userId(userId).exerciseId(exId).build();
                currentPb.setOneRm(bestOneRm);
                currentPbByExercise.put(exId, currentPb);
            }

            Double totalVolume = AchievementCalculator.computeTotalVolume(reps, weight);
            Double previousVolume = (currentPb != null) ? currentPb.getTotalVolume() : null;
            if (totalVolume != null && (previousVolume == null || totalVolume > previousVolume)) {
                record.setAchievedTotalVolumeValue(totalVolume);
                if (currentPb == null) currentPb = ExercisePersonalBest.builder().userId(userId).exerciseId(exId).build();
                currentPb.setTotalVolume(totalVolume);
                currentPbByExercise.put(exId, currentPb);
            }
        }

        // Save records
        List<ExerciseRecord> savedRecords = exerciseRecordRepository.saveAll(exerciseRecords);

        // Persist PB documents for records that achieved PRs
        personalBestService.persistPrs(userId, savedRecords);

        // Update streaks for the user based on the completed workout and capture the update status
        var streakUpdate = streakService.onWorkoutCompleted(workosId, workoutId);

        // Build response from saved data
        Set<UUID> savedExIds = savedRecords.stream().map(ExerciseRecord::getExerciseId).collect(Collectors.toSet());
        Map<UUID, String> exerciseNameById = new HashMap<>();
        exerciseRepository.findAllById(savedExIds).forEach(ex -> exerciseNameById.put(ex.getId(), ex.getName()));

        var exerciseResponses = savedRecords.stream()
                .map(exRecord -> UserWorkoutRecordsResponse.ExerciseRecordResponse.builder()
                        .exerciseName(exerciseNameById.getOrDefault(exRecord.getExerciseId(), "Unknown"))
                        .reps(exRecord.getReps())
                        .weight(exRecord.getWeight())
                        .durationSeconds(exRecord.getDurationSeconds())
                        .notes(exRecord.getNotes())
                        .achievedOneRmValue(exRecord.getAchievedOneRmValue())
                        .achievedOneRmSetIndex(exRecord.getAchievedOneRmSetIndex())
                        .achievedTotalVolumeValue(exRecord.getAchievedTotalVolumeValue())
                        .build())
                .collect(Collectors.toList());

        var recordResponse = UserWorkoutRecordsResponse.builder()
                .id(saved.getId().toString())
                .workoutId(saved.getWorkoutId().toString())
                .workoutTitle(saved.getWorkoutTitle())
                .notes(saved.getNotes())
                .startTime(saved.getStartTime())
                .createdAt(saved.getCreatedAt())
                .exerciseRecords(exerciseResponses)
                .build();

        return com.services.active.dto.WorkoutRecordCreateResponse.builder()
                .workoutRecord(recordResponse)
                .streakUpdate(streakUpdate)
                .build();
    }

    public List<UserWorkoutRecordsResponse> getWorkoutRecords(String workosId) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        UUID userId = user.getId();

        return workoutRecordRepository.findAllByUserId(userId)
                .stream()
                .map(workoutRecord -> {
                    // Use proper FK join instead of fetching by ID list
                    var exRecords = exerciseRecordRepository.findAllByWorkoutRecordIdOrderByOrdinalAsc(workoutRecord.getId());

                    // Batch-load all exercises used in this workout record to avoid N+1
                    Set<UUID> exIds = exRecords.stream().map(ExerciseRecord::getExerciseId).collect(Collectors.toSet());
                    Map<UUID, String> exerciseNameById = new HashMap<>();
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
                                        .achievedOneRmValue(exRecord.getAchievedOneRmValue())
                                        .achievedOneRmSetIndex(exRecord.getAchievedOneRmSetIndex())
                                        .achievedTotalVolumeValue(exRecord.getAchievedTotalVolumeValue())
                                        .build();
                            })
                            .collect(Collectors.toList());

                    return UserWorkoutRecordsResponse.builder()
                            .id(workoutRecord.getId().toString())
                            .workoutTitle(workoutRecord.getWorkoutTitle())
                            .workoutId(workoutRecord.getWorkoutId().toString())
                            .notes(workoutRecord.getNotes())
                            .startTime(workoutRecord.getStartTime())
                            .createdAt(workoutRecord.getCreatedAt())
                            .exerciseRecords(exerciseResponses)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteWorkoutRecord(String workosId, String workoutRecordId) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        UUID recordId = UUID.fromString(workoutRecordId);
        WorkoutRecord workoutRecord = workoutRecordRepository.findById(recordId)
                .orElseThrow(() -> new NotFoundException("Workout record not found"));

        if (!user.getId().equals(workoutRecord.getUserId())) {
            throw new UnauthorizedException("Not authorized to delete this workout record");
        }

        // Exercise records are deleted via CASCADE, but explicit delete is also fine
        exerciseRecordRepository.deleteAll(
                exerciseRecordRepository.findAllByWorkoutRecordIdOrderByOrdinalAsc(workoutRecord.getId()));

        workoutRecordRepository.deleteById(recordId);
    }
}
