package com.services.active.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.services.active.config.IntegrationTestBase;
import com.services.active.config.user.TestUserContext;
import com.services.active.config.user.WithTestUser;
import com.services.active.dto.CreateRoutineRequest;
import com.services.active.dto.CreateWorkoutRequest;
import com.services.active.dto.CreateWorkoutTemplateRequest;
import com.services.active.models.RoutinePattern;
import com.services.active.models.TemplateExercise;
import com.services.active.models.types.DayType;
import com.services.active.models.user.User;
import com.services.active.repository.*;
import com.services.active.services.RoutineService;
import com.services.active.services.WorkoutService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithTestUser
@SpringBootTest
@AutoConfigureMockMvc
class UserDeleteControllerIT extends IntegrationTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserRepository userRepository;
    @Autowired private WorkoutRepository workoutRepository;
    @Autowired private WorkoutTemplateRepository workoutTemplateRepository;
    @Autowired private WorkoutRecordRepository workoutRecordRepository;
    @Autowired private ExerciseRecordRepository exerciseRecordRepository;
    @Autowired private ExercisePersonalBestRepository exercisePersonalBestRepository;
    @Autowired private RoutineRepository routineRepository;

    @Autowired private WorkoutService workoutService;
    @Autowired private RoutineService routineService;

    @Test
    @DisplayName("DELETE /api/user/me removes user and all related data from DB")
    void deleteUserAndAllData_success(@TestUserContext String token, @TestUserContext User user) throws Exception {
        // 1) Create a workout for the current user
        TemplateExercise exercise1 = TemplateExercise.builder()
                .exerciseId("ex-1")
                .reps(List.of(10, 8, 6))
                .weight(List.of(50.0, 55.0, 60.0))
                .notes("Bench press")
                .build();
        TemplateExercise exercise2 = TemplateExercise.builder()
                .exerciseId("ex-2")
                .reps(List.of(12, 10, 8))
                .weight(List.of(20.0, 22.5, 25.0))
                .notes("Rows")
                .build();
        CreateWorkoutTemplateRequest template = CreateWorkoutTemplateRequest.builder()
                .exercises(List.of(exercise1, exercise2))
                .build();
        CreateWorkoutRequest workoutReq = CreateWorkoutRequest.builder()
                .title("Push Day")
                .notes("Focus on form")
                .template(template)
                .build();
        var workout = workoutService.createWorkout(user.getId(), workoutReq);

        // 2) Create a workout record via API (also creates exercise records and personal bests)
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(30);
        String payload = ("""
                {
                  "workoutId": "%s",
                  "notes": "Great session",
                  "startTime": "%s",
                  "exerciseRecords": [
                    { "exerciseId": "ex-1", "reps": [10,8,6], "weight": [50.0,55.0,62.5], "notes": "Felt strong" },
                    { "exerciseId": "ex-2", "reps": [12,10,8], "weight": [20.0,22.5,25.0], "notes": "Solid" }
                  ]
                }
                """).formatted(workout.getId(), startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        String response = mockMvc.perform(post("/api/workouts/record")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        assertThat(node.get("workoutRecord")).isNotNull();

        // 3) Create a routine for the user
        RoutinePattern p = RoutinePattern.builder()
                .dayIndex(0)
                .dayType(DayType.WORKOUT)
                .workoutId(workout.getId())
                .build();
        CreateRoutineRequest routineReq = CreateRoutineRequest.builder()
                .name("My Routine")
                .description("Test routine")
                .pattern(List.of(p))
                .active(true)
                .build();
        routineService.createRoutine(user.getId(), routineReq);

        // Sanity checks before deletion
        assertThat(userRepository.findById(user.getId())).isPresent();
        assertThat(workoutRepository.findAllByUserId(user.getId())).isNotEmpty();
        assertThat(workoutRecordRepository.findAllByUserId(user.getId())).isNotEmpty();
        assertThat(exerciseRecordRepository.findByUserIdOrderByCreatedAtAsc(user.getId())).isNotEmpty();
        assertThat(exercisePersonalBestRepository.findAllByUserId(user.getId())).isNotEmpty();
        assertThat(routineRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())).isNotEmpty();
        assertThat(workoutTemplateRepository.findAll()).isNotEmpty();

        // 4) Delete the current user and all data
        mockMvc.perform(delete("/api/user/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify everything is gone for that user
        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(workoutRepository.findAllByUserId(user.getId())).isEmpty();
        assertThat(workoutRecordRepository.findAllByUserId(user.getId())).isEmpty();
        assertThat(exerciseRecordRepository.findByUserIdOrderByCreatedAtAsc(user.getId())).isEmpty();
        assertThat(exercisePersonalBestRepository.findAllByUserId(user.getId())).isEmpty();
        assertThat(routineRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())).isEmpty();
        // All templates created for this user's workouts should also be gone
        assertThat(workoutTemplateRepository.findAll()).isEmpty();
    }
}

