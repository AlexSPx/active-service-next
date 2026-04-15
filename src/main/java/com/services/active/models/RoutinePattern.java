package com.services.active.models;

import com.services.active.models.types.DayType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "routine_patterns")
public class RoutinePattern {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "routine_id", nullable = false)
    private UUID routineId;

    private int dayIndex;

    @Enumerated(EnumType.STRING)
    private DayType dayType;

    @Column(name = "workout_id")
    private UUID workoutId;
}
