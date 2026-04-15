package com.services.active.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.services.active.models.types.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "exercises")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Exercise {
    @Id
    private UUID id;

    private String name;

    @Enumerated(EnumType.STRING)
    private Level level;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private ForceType force; // Nullable

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private MechanicType mechanic; // Nullable

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Equipment equipment; // Nullable

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> primaryMuscles;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> secondaryMuscles;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> instructions;

    @Enumerated(EnumType.STRING)
    private Category category;

    /**
     * The remote exercise JSON uses a human-readable slug as "id" (e.g. "3_4_Sit-Up").
     * We ignore that during deserialization and assign a proper UUID in ExerciseLoaderConfig.
     * This setter absorbs the JSON "id" string without crashing.
     */
    @JsonProperty("id")
    public void setJsonId(String jsonId) {
        // Intentionally ignored — UUIDs are assigned by ExerciseLoaderConfig
    }
}