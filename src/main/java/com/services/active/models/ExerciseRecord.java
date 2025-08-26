package com.services.active.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "exercise_records")
public class ExerciseRecord {
    @Id
    private String id;
    
    @Indexed
    private String userId;

    @Indexed
    private String exerciseId;

    private LocalDateTime createdAt;
    
    // Strength training fields
    private List<Integer> reps;
    private List<Double> weight;

    // Cardio/Time-based fields
    private List<Integer> durationSeconds;

    // Common fields
    private String notes;

    // Achievement sub-docs (only present when this record set a new PR)
    // One-Rep Max (Epley estimate) per-set achievement
    private OneRmAchievement achievedOneRm;
    // Total volume across this exercise record (sum of reps*weight)
    private TotalVolumeAchievement achievedTotalVolume;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OneRmAchievement {
        private Double value; // estimated 1RM in kg
        private Integer setIndex; // zero-based index of the set that achieved it
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TotalVolumeAchievement {
        private Double value; // total volume in kg across all sets
    }
}
