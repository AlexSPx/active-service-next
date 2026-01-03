package com.services.active.models;

import com.services.active.models.types.RoutineType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document
public class Routine {
    @Id
    private String id;
    private String name;
    private String description;
    private String userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Instant startDate;
    private List<RoutinePattern> pattern;

    /**
     * The type of routine scheduling.
     * SEQUENTIAL: Workouts assigned to specific days in a repeating cycle.
     * WEEKLY_COMPLETION: All workouts must be completed within a week (Mon-Sun), any order.
     * Defaults to SEQUENTIAL.
     */
    @Builder.Default
    private RoutineType routineType = RoutineType.SEQUENTIAL;
}
