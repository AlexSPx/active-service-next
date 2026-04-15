package com.services.active.models;

import com.services.active.models.types.RoutineType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "routines")
public class Routine {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;
    private String description;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Instant startDate;

    /**
     * The type of routine scheduling.
     * SEQUENTIAL: Workouts assigned to specific days in a repeating cycle.
     * WEEKLY_COMPLETION: All workouts must be completed within a week (Mon-Sun), any order.
     * Defaults to SEQUENTIAL.
     */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RoutineType routineType = RoutineType.SEQUENTIAL;
}
