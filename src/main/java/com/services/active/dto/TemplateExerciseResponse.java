package com.services.active.dto;

import com.services.active.models.types.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateExerciseResponse {
    private String exerciseId;
    private List<Integer> reps;
    private List<Double> weight;
    private List<Integer> durationSeconds;
    private String notes;
    private Category category;
}

