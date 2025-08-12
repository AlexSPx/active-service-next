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
@Document(collection = "workouts")
public class Workout {
    @Id
    private String id;

    private String title;

    private String notes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;


    private List<String> workoutRecordIds; // References to WorkoutRecord documents

    @Indexed
    private String userId;

    @Indexed
    private String templateId; // Reference to WorkoutTemplate

}
