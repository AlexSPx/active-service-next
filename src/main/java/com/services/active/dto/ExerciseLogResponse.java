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
public class ExerciseLogResponse {
    private String exerciseRecordId;
    private String exerciseId;
    private String exerciseName;
    private LocalDateTime createdAt;
    
    // Strength training fields
    private List<Integer> reps;
    private List<Double> weight;
    
    // Cardio/Time-based fields
    private List<Integer> durationSeconds;
    
    // Common fields
    private String notes;
    
    // Achievement fields (only present when this record set a new PR)
    private Double achievedOneRmValue; // estimated 1RM in kg
    private Integer achievedOneRmSetIndex; // zero-based set index that achieved 1RM
    private Double achievedTotalVolumeValue; // total volume in kg across all sets
}
