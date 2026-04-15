package com.services.active.models.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workos_id", unique = true, nullable = false)
    private String workosId;

    private String username;

    private LocalDate createdAt;

    private String timezone;

    @Column(name = "active_routine_id")
    private UUID activeRoutineId;

    @Builder.Default
    private boolean registrationCompleted = false;

    // Streak Info (flattened from StreakInfo sub-document)
    @Builder.Default
    private int currentStreak = 0;
    @Builder.Default
    private int longestStreak = 0;
    private UUID nextWorkoutId;
    private LocalDate nextWorkoutDeadline;
    @Builder.Default
    private int streakFreezeCount = 0;
    private LocalDate lastWorkoutCountedDate;
    private LocalDate currentWeekStart;

    // Body Measurements (flattened from BodyMeasurements sub-document)
    private Double weightKg;
    private Integer heightCm;

    // Notification Preferences (flag flattened; schedule in separate table)
    @Builder.Default
    private boolean emailNotificationsEnabled = false;
}
