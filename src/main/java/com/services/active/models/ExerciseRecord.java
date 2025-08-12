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
}
