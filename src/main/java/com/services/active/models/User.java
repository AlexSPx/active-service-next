package com.services.active.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.services.active.models.types.AuthProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Document("users")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @Id
    private String id;
    private String email;
    private String username;

    private String firstName;
    private String lastName;

    @JsonIgnore
    private String passwordHash;
    @JsonIgnore
    private AuthProvider provider;

    private LocalDate createdAt;

    @Indexed
    private String timezone;

    private String activeRoutineId;

    @Builder.Default
    private StreakInfo streak = new StreakInfo();

    // New field to store push notification tokens (one user can have multiple devices)
    @Builder.Default
    private List<String> pushTokens = new ArrayList<>();

    // Nested body measurements (optional). Null if not provided at signup.
    private BodyMeasurements measurements;
}
