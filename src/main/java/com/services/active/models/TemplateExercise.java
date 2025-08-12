package com.services.active.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateExercise {
    private String exerciseId;

    // Strength training fields
    private List<Integer> reps;
    private List<Double> weight;

    // Cardio/Time-based fields
    private List<Integer> durationSeconds;

    // Common fields
    private String notes;
}
