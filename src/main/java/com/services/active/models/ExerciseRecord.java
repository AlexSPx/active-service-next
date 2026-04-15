package com.services.active.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "exercise_records")
public class ExerciseRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "workout_record_id", nullable = false)
    private UUID workoutRecordId;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(nullable = false)
    @Builder.Default
    private int ordinal = 0;

    private LocalDateTime createdAt;

    // Strength training fields
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "integer[]")
    private List<Integer> reps;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "double precision[]")
    private List<Double> weight;

    // Cardio/Time-based fields
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "integer[]")
    private List<Integer> durationSeconds;

    // Common fields
    private String notes;

    // Achievement data (flattened from OneRmAchievement / TotalVolumeAchievement sub-docs)
    private Double achievedOneRmValue;
    private Integer achievedOneRmSetIndex;
    private Double achievedTotalVolumeValue;
}
