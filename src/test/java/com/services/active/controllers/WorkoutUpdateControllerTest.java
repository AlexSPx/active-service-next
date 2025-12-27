package com.services.active.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.services.active.config.IntegrationTestBase;
import com.services.active.config.user.TestUserContext;
import com.services.active.config.user.WithTestUser;
import com.services.active.dto.CreateWorkoutRequest;
import com.services.active.dto.CreateWorkoutTemplateRequest;
import com.services.active.dto.UpdateWorkoutRequest;
import com.services.active.models.TemplateExercise;
import com.services.active.models.user.User;
import com.services.active.models.Workout;
import com.services.active.models.WorkoutTemplate;
import com.services.active.repository.WorkoutRepository;
import com.services.active.repository.WorkoutTemplateRepository;
import com.services.active.services.WorkoutService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;


import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithTestUser
@SpringBootTest
@AutoConfigureMockMvc
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class WorkoutUpdateControllerTest extends IntegrationTestBase {

    private final MockMvc mockMvc;

    private final WorkoutService workoutService;
    private final WorkoutRepository workoutRepository;
    private final WorkoutTemplateRepository workoutTemplateRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Workout createWorkout(User user) {
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
                .title("Original Title")
                .notes("Original notes")
                .template(template)
                .build();

        return workoutService.createWorkout(user.getWorkosId(), request);
    }

    @Test
    @DisplayName("PUT /api/workouts/{id} -> 200 OK when template missing (partial update)")
    void updateWorkout_missingTemplate_partialUpdate(@TestUserContext String token, @TestUserContext User user) throws Exception {
        Workout workout = createWorkout(user);
        WorkoutTemplate originalTemplate = workoutTemplateRepository.getWorkoutTemplateById(workout.getTemplateId());
        // Baseline: template has exercise-1

        UpdateWorkoutRequest request = UpdateWorkoutRequest.builder()
                .title("Updated Title")
                .notes("Updated notes")
                .build(); // template missing

        mockMvc.perform(put("/api/workouts/" + workout.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(workout.getId()))
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.notes").value("Updated notes"));

        // Template unchanged
        Workout updateWorkout = workoutRepository.getWorkoutById((workout.getId()));
        WorkoutTemplate workoutTemplate = workoutTemplateRepository.getWorkoutTemplateById(updateWorkout.getTemplateId());

        assertThat(workoutTemplate.getUpdatedAt()).isEqualTo(originalTemplate.getUpdatedAt());
    }

    @Test
    @DisplayName("PUT /api/workouts/{id} -> 200 OK updates workout and template when provided")
    void updateWorkout_success(@TestUserContext String token, @TestUserContext User user) throws Exception {
        Workout workout = createWorkout(user);
        WorkoutTemplate beforeTemplate = workoutTemplateRepository.getWorkoutTemplateById(workout.getTemplateId());

        TemplateExercise updatedExercise = TemplateExercise.builder()
                .exerciseId("exercise-2")
                .reps(List.of(12, 10))
                .weight(List.of(40.0, 45.0))
                .notes("New notes")
                .build();

        CreateWorkoutTemplateRequest updatedTemplate = CreateWorkoutTemplateRequest.builder()
                .exercises(List.of(updatedExercise))
                .build();

        UpdateWorkoutRequest request = UpdateWorkoutRequest.builder()
                .title("Updated Title")
                .notes("Updated notes")
                .template(updatedTemplate)
                .build();

        mockMvc.perform(put("/api/workouts/" + workout.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(workout.getId()))
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.notes").value("Updated notes"));

        // Verify via repositories that template is updated
        Workout updatedWorkout = workoutRepository.getWorkoutById(workout.getId());
        WorkoutTemplate afterTemplate = workoutTemplateRepository.getWorkoutTemplateById(updatedWorkout.getTemplateId());

        assertThat(afterTemplate.getUpdatedAt()).isAfterOrEqualTo(beforeTemplate.getUpdatedAt());
        assertThat(afterTemplate.getExercises()).hasSize(1);
        assertThat(afterTemplate.getExercises().get(0).getExerciseId()).isEqualTo("exercise-2");
        assertThat(afterTemplate.getExercises().get(0).getReps()).isNotEmpty();
        assertThat(afterTemplate.getExercises().get(0).getReps().get(0)).isEqualTo(12);
    }

    @Test
    @DisplayName("PUT /api/workouts/{id} -> 200 OK does not change template when exercises is empty")
    void updateWorkout_emptyTemplateExercises_noChange(@TestUserContext String token, @TestUserContext User user) throws Exception {
        Workout workout = createWorkout(user);
        WorkoutTemplate beforeTemplate = workoutTemplateRepository.getWorkoutTemplateById(workout.getTemplateId());

        CreateWorkoutTemplateRequest emptyTemplate = CreateWorkoutTemplateRequest.builder()
                .exercises(List.of())
                .build();

        UpdateWorkoutRequest request = UpdateWorkoutRequest.builder()
                .title("Another Title")
                .template(emptyTemplate)
                .build();

        mockMvc.perform(put("/api/workouts/" + workout.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Another Title"));

        // Template should remain unchanged
        Workout afterWorkout = workoutRepository.getWorkoutById(workout.getId());
        WorkoutTemplate afterTemplate = workoutTemplateRepository.getWorkoutTemplateById(afterWorkout.getTemplateId());
        assertThat(afterTemplate.getUpdatedAt()).isEqualTo(beforeTemplate.getUpdatedAt());
    }
}
