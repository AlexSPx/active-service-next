package com.services.active.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.services.active.config.IntegrationTestBase;
import com.services.active.config.user.TestUserContext;
import com.services.active.config.user.WithTestUser;
import com.services.active.dto.CreateWorkoutRequest;
import com.services.active.dto.CreateWorkoutTemplateRequest;
import com.services.active.models.*;
import com.services.active.repository.ExerciseRecordRepository;
import com.services.active.repository.WorkoutRecordRepository;
import com.services.active.services.WorkoutService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithTestUser
@SpringBootTest
@AutoConfigureMockMvc
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class WorkoutRecordControllerTest extends IntegrationTestBase {

    private final MockMvc mockMvc;

    private final WorkoutService workoutService;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;

    private final ObjectMapper objectMapper;

    @org.junit.jupiter.api.BeforeEach
    void setupMapper() {
        objectMapper.findAndRegisterModules();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Test
    @DisplayName("POST /api/workouts/record -> 201 CREATED successfully creates workout record with exercise records")
    void createWorkoutRecord_success(@TestUserContext String token, @TestUserContext User user) throws Exception {
        // First, create a workout using service calls
        TemplateExercise exercise1 = TemplateExercise.builder()
                .exerciseId("exercise-1")
                .reps(List.of(10, 8, 6))
                .weight(List.of(50.0, 55.0, 60.0))
                .notes("Bench press")
                .build();

        TemplateExercise exercise2 = TemplateExercise.builder()
                .exerciseId("exercise-2")
                .reps(List.of(12, 10, 8))
                .weight(List.of(20.0, 22.5, 25.0))
                .notes("Dumbbell rows")
                .build();

        CreateWorkoutTemplateRequest template = CreateWorkoutTemplateRequest.builder()
                .exercises(List.of(exercise1, exercise2))
                .build();

        CreateWorkoutRequest workoutRequest = CreateWorkoutRequest.builder()
                .title("Push Day Workout")
                .notes("Focus on form and progressive overload")
                .template(template)
                .build();

        // Create the workout through service
        Workout createdWorkout = workoutService.createWorkout("testuser", workoutRequest);

        // Build JSON payload manually to avoid LocalDateTime serialization issues in test mapper
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(45);
        String payload = """
                {
                  "workoutId": "%s",
                  "notes": "Great session, feeling stronger",
                  "startTime": "%s",
                  "exerciseRecords": [
                    {
                      "exerciseId": "exercise-1",
                      "reps": [10, 8, 6],
                      "weight": [50.0, 55.0, 62.5],
                      "notes": "Felt strong today, increased final set weight"
                    },
                    {
                      "exerciseId": "exercise-2",
                      "reps": [12, 10, 8],
                      "weight": [20.0, 22.5, 25.0],
                      "notes": "Good form maintained throughout"
                    }
                  ]
                }
                """.formatted(createdWorkout.getId(), startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // Make request to create workout record
        String responseContent = mockMvc.perform(post("/api/workouts/record")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract the workout record ID from response
        String workoutRecordId = responseContent.replace("\"", "");

        // Validate the workout record was saved correctly in the database
        WorkoutRecord savedWorkoutRecord = workoutRecordRepository.findById(workoutRecordId).orElse(null);
        assertThat(savedWorkoutRecord).isNotNull();
        assertThat(savedWorkoutRecord.getId()).isEqualTo(workoutRecordId);
        assertThat(savedWorkoutRecord.getUserId()).isEqualTo(user.getId());
        assertThat(savedWorkoutRecord.getWorkoutId()).isEqualTo(createdWorkout.getId());
        assertThat(savedWorkoutRecord.getNotes()).isEqualTo("Great session, feeling stronger");
        assertThat(savedWorkoutRecord.getStartTime()).isNotNull();
        assertThat(savedWorkoutRecord.getCreatedAt()).isNotNull();
        assertThat(savedWorkoutRecord.getExerciseRecordIds()).hasSize(2);

        // Validate the exercise records were saved correctly
        List<ExerciseRecord> exerciseRecords = exerciseRecordRepository.findAllById(savedWorkoutRecord.getExerciseRecordIds());
        assertThat(exerciseRecords).hasSize(2);

        // Validate first exercise record
        ExerciseRecord firstExercise = exerciseRecords.stream()
                .filter(er -> "exercise-1".equals(er.getExerciseId()))
                .findFirst()
                .orElse(null);
        assertThat(firstExercise).isNotNull();
        assertThat(firstExercise.getUserId()).isEqualTo(user.getId());
        assertThat(firstExercise.getReps()).containsExactly(10, 8, 6);
        assertThat(firstExercise.getWeight()).containsExactly(50.0, 55.0, 62.5);
        assertThat(firstExercise.getNotes()).isEqualTo("Felt strong today, increased final set weight");
        assertThat(firstExercise.getCreatedAt()).isNotNull();

        // Validate second exercise record
        ExerciseRecord secondExercise = exerciseRecords.stream()
                .filter(er -> "exercise-2".equals(er.getExerciseId()))
                .findFirst()
                .orElse(null);
        assertThat(secondExercise).isNotNull();
        assertThat(secondExercise.getUserId()).isEqualTo(user.getId());
        assertThat(secondExercise.getReps()).containsExactly(12, 10, 8);
        assertThat(secondExercise.getWeight()).containsExactly(20.0, 22.5, 25.0);
        assertThat(secondExercise.getNotes()).isEqualTo("Good form maintained throughout");
        assertThat(secondExercise.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("POST /api/workouts/record -> 400 BAD REQUEST when workout ID is missing")
    void createWorkoutRecord_missingWorkoutId_badRequest(@TestUserContext String token) throws Exception {
        String payload = """
                {
                  "notes": "Test workout record",
                  "startTime": "%s",
                  "exerciseRecords": [
                    {
                      "exerciseId": "exercise-1",
                      "reps": [10, 8, 6],
                      "weight": [50.0, 55.0, 60.0]
                    }
                  ]
                }
                """.formatted(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        mockMvc.perform(post("/api/workouts/record")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("POST /api/workouts/record -> 401 UNAUTHORIZED when no auth token provided")
    void createWorkoutRecord_noAuth_unauthorized() throws Exception {
        String payload = """
                {
                  "workoutId": "test-workout-id",
                  "notes": "Test workout record",
                  "startTime": "%s",
                  "exerciseRecords": []
                }
                """.formatted(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        mockMvc.perform(post("/api/workouts/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andDo(print());
    }
}
