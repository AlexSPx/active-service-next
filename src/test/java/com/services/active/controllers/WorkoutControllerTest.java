package com.services.active.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.services.active.config.IntegrationTestBase;
import com.services.active.config.user.TestUserContext;
import com.services.active.config.user.WithTestUser;
import com.services.active.dto.CreateWorkoutRequest;
import com.services.active.dto.CreateWorkoutTemplateRequest;
import com.services.active.models.TemplateExercise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithTestUser
@SpringBootTest
@AutoConfigureMockMvc
class WorkoutControllerTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("POST /api/workouts -> 400 BAD REQUEST when template is missing")
    void createWorkout_missingTemplate_badRequest(@TestUserContext String token) throws Exception {
        CreateWorkoutRequest request = CreateWorkoutRequest.builder()
                .title("Push Day Workout")
                .notes("Focus on form and progressive overload")
                .build();

        mockMvc.perform(post("/api/workouts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Template is required"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "null"})
    @DisplayName("POST /api/workouts -> 400 BAD REQUEST when title is missing")
    void createWorkout_missingTitle_badRequest(String title, @TestUserContext String token) throws Exception {
        TemplateExercise exercise = TemplateExercise.builder()
                .exerciseId("exercise-1")
                .reps(List.of(10, 8, 6))
                .weight(List.of(50.0, 55.0, 60.0))
                .notes("Warm up properly")
                .build();

        CreateWorkoutTemplateRequest template = CreateWorkoutTemplateRequest.builder()
                .exercises(List.of(exercise))
                .build();

        CreateWorkoutRequest request = CreateWorkoutRequest.builder()
                .title("null".equals(title) ? null : title)
                .notes("No title provided")
                .template(template)
                .build();

        mockMvc.perform(post("/api/workouts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Title is required"));
    }

    @Test
    @DisplayName("POST /api/workouts -> 201 CREATED returns workout on success when template provided")
    void createWorkout_success_withTemplate(@TestUserContext String token) throws Exception {
        TemplateExercise exercise = TemplateExercise.builder()
                .exerciseId("exercise-1")
                .reps(List.of(10, 8, 6))
                .weight(List.of(50.0, 55.0, 60.0))
                .notes("Warm up properly")
                .build();

        CreateWorkoutTemplateRequest template = CreateWorkoutTemplateRequest.builder()
                .exercises(List.of(exercise))
                .build();

        CreateWorkoutRequest request = CreateWorkoutRequest.builder()
                .title("Push Day Workout")
                .notes("Focus on form and progressive overload")
                .template(template)
                .build();

        mockMvc.perform(post("/api/workouts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Push Day Workout"));
    }
}
