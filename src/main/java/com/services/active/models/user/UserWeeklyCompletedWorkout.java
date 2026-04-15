package com.services.active.models.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "user_weekly_completed_workouts")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserWeeklyCompletedWorkout {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "workout_id", nullable = false)
    private UUID workoutId;
}
