package com.services.active.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.services.active.models.types.AuthProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

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

    private String timezone;

    private String activeRoutineId;

    @Builder.Default
    private StreakInfo streak = new StreakInfo();
}
