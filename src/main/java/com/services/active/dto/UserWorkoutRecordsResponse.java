package com.services.active.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWorkoutRecordsResponse {
    private String id;
    private String workoutId;
    private String workoutTitle;
    private String notes;
    private LocalDateTime startTime;
    private LocalDateTime createdAt;
    private List<ExerciseRecordResponse> exerciseRecords;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExerciseRecordResponse {
        private String exerciseName;

        private List<Integer> reps;
        private List<Double> weight;

        private List<Integer> durationSeconds;

        private String notes;

        // Achievement fields (only populated if this record set a new PR)
        private Double achievedOneRmValue; // estimated 1RM in kg
        private Integer achievedOneRmSetIndex; // zero-based set index that achieved 1RM
        private Double achievedTotalVolumeValue; // total volume in kg across all sets
    }
}
