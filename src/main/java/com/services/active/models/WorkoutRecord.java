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
@Document(collection = "workout_records")
public class WorkoutRecord {
    @Id
    private String id;

    @Indexed
    private String userId;

    private String workoutId;

    private String notes;

    private List<String> exerciseRecordIds; // References to ExerciseRecord documents

    private LocalDateTime createdAt;
}
