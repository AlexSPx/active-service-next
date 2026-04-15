package com.services.active.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "exercise_personal_bests",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "exercise_id"}))
public class ExercisePersonalBest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    // Best estimated 1RM (kg) and the record that achieved it
    private Double oneRm;
    private UUID oneRmRecordId;
    private Integer oneRmRecordSetIndex;

    // Best total volume (kg) across all sets in a record and the record that achieved it
    private Double totalVolume;
    private UUID totalVolumeRecordId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
