package com.services.active.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkoutRecordRequest {
    private String notes;
    private String workoutId;
    private LocalDateTime startTime;
    private List<ExerciseRecord> exerciseRecords;


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ExerciseRecord {
        private String exerciseId;
        private List<Integer> reps;
        private List<Double> weight; // in kg
        private List<Integer> durationSeconds; // for cardio exercises, in seconds
        private String notes; // optional notes for the exercise
    }
}
