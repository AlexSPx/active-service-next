package com.services.active.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.services.active.config.IntegrationTestBase;
import com.services.active.config.user.TestUserContext;
import com.services.active.config.user.WithTestUser;
import com.services.active.models.Exercise;
import com.services.active.models.ExerciseRecord;
import com.services.active.models.user.User;
import com.services.active.models.types.Category;
import com.services.active.models.types.Equipment;
import com.services.active.models.types.Level;
import com.services.active.models.types.MuscleGroup;
import com.services.active.repository.ExerciseRecordRepository;
import com.services.active.repository.ExerciseRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithTestUser
@SpringBootTest
@AutoConfigureMockMvc
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class ExerciseLogControllerIT extends IntegrationTestBase {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final ExerciseRepository exerciseRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;

    private Exercise testExercise;

    @BeforeEach
    void setupTestData() {
        // Create a test exercise
        testExercise = Exercise.builder()
                .id("test-exercise-id")
                .name("Bench Press")
                .category(Category.STRENGTH)
                .level(Level.INTERMEDIATE)
                .primaryMuscles(List.of(MuscleGroup.CHEST))
                .secondaryMuscles(List.of(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS))
                .equipment(Equipment.BARBELL)
                .instructions(List.of("Lie on bench", "Press weight"))
                .build();
        exerciseRepository.save(testExercise);
    }

    @Test
    @DisplayName("GET /api/exercises/{exerciseId}/logs returns empty list when no logs exist")
    void getExerciseLogs_noLogs_returnsEmptyList(@TestUserContext String token) throws Exception {
        mockMvc.perform(get("/api/exercises/{exerciseId}/logs", testExercise.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)))
                .andDo(print());
    }

    @Test
    @DisplayName("GET /api/exercises/{exerciseId}/logs returns exercise logs ordered by creation time")
    void getExerciseLogs_withLogs_returnsOrderedLogs(@TestUserContext String token, @TestUserContext User user) throws Exception {
        // Create test exercise records
        LocalDateTime baseTime = LocalDateTime.now().minusDays(2);
        
        ExerciseRecord record1 = ExerciseRecord.builder()
                .userId(user.getId())
                .exerciseId(testExercise.getId())
                .createdAt(baseTime)
                .reps(List.of(10, 8, 6))
                .weight(List.of(100.0, 105.0, 110.0))
                .notes("First session")
                .build();

        ExerciseRecord record2 = ExerciseRecord.builder()
                .userId(user.getId())
                .exerciseId(testExercise.getId())
                .createdAt(baseTime.plusDays(1))
                .reps(List.of(12, 10, 8))
                .weight(List.of(95.0, 100.0, 105.0))
                .notes("Second session")
                .build();

        exerciseRecordRepository.saveAll(List.of(record1, record2));

        mockMvc.perform(get("/api/exercises/{exerciseId}/logs", testExercise.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].notes", is("First session")))
                .andExpect(jsonPath("$[0].exerciseName", is("Bench Press")))
                .andExpect(jsonPath("$[0].reps", contains(10, 8, 6)))
                .andExpect(jsonPath("$[0].weight", contains(100.0, 105.0, 110.0)))
                .andExpect(jsonPath("$[1].notes", is("Second session")))
                .andDo(print());
    }

    @Test
    @DisplayName("GET /api/exercises/{exerciseId}/logs returns 404 for non-existent exercise")
    void getExerciseLogs_nonExistentExercise_returns404(@TestUserContext String token) throws Exception {
        mockMvc.perform(get("/api/exercises/{exerciseId}/logs", "non-existent-id")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andDo(print());
    }

    @Test
    @DisplayName("GET /api/exercises/{exerciseId}/logs returns 401 when no auth token provided")
    void getExerciseLogs_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/exercises/{exerciseId}/logs", testExercise.getId()))
                .andExpect(status().isUnauthorized())
                .andDo(print());
    }

    @Test
    @DisplayName("GET /api/exercises/{exerciseId}/logs only returns logs for authenticated user")
    void getExerciseLogs_onlyUserLogs_returnsUserSpecificLogs(@TestUserContext String token, @TestUserContext User user) throws Exception {
        // Create records for the test user
        ExerciseRecord userRecord = ExerciseRecord.builder()
                .userId(user.getId())
                .exerciseId(testExercise.getId())
                .createdAt(LocalDateTime.now())
                .reps(List.of(10))
                .weight(List.of(100.0))
                .notes("User record")
                .build();

        // Create records for another user
        ExerciseRecord otherUserRecord = ExerciseRecord.builder()
                .userId("other-user-id")
                .exerciseId(testExercise.getId())
                .createdAt(LocalDateTime.now())
                .reps(List.of(15))
                .weight(List.of(80.0))
                .notes("Other user record")
                .build();

        exerciseRecordRepository.saveAll(List.of(userRecord, otherUserRecord));

        mockMvc.perform(get("/api/exercises/{exerciseId}/logs", testExercise.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].notes", is("User record")))
                .andDo(print());
    }
}