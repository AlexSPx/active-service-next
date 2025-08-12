package com.services.active.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.services.active.models.types.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document("exercises")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Exercise {
    @Id
    private String id;
    private String name;
    private Level level;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private ForceType force; // Nullable
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private MechanicType mechanic; // Nullable
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Equipment equipment; // Nullable

    private List<MuscleGroup> primaryMuscles;
    private List<MuscleGroup> secondaryMuscles;

    private List<String> instructions;

    private Category category;
}