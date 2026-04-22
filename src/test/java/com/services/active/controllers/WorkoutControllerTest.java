package com.services.active.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.services.active.config.IntegrationTestBase;
import com.services.active.config.user.TestUserContext;
import com.services.active.config.user.WithTestUser;
import com.services.active.dto.CreateWorkoutRequest;
import com.services.active.dto.CreateWorkoutTemplateRequest;
import com.services.active.models.Exercise;
import com.services.active.models.Workout;
import com.services.active.repository.ExerciseRepository;
import com.services.active.services.WorkoutService;
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
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithTestUser
@SpringBootTest
@AutoConfigureMockMvc
class WorkoutControllerTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private WorkoutService workoutService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Workout createWorkout(String workosId, String title, UUID exerciseId, String exerciseName) {
        exerciseRepository.save(Exercise.builder()
                .id(exerciseId)
                .name(exerciseName)
                .build());

        CreateWorkoutTemplateRequest.TemplateExerciseRequest exercise = CreateWorkoutTemplateRequest.TemplateExerciseRequest.builder()
                .exerciseId(exerciseId)
                .reps(List.of(10, 8, 6))
                .weight(List.of(50.0, 55.0, 60.0))
                .notes("Warm up properly")
                .build();

        CreateWorkoutTemplateRequest template = CreateWorkoutTemplateRequest.builder()
                .exercises(List.of(exercise))
                .build();

        CreateWorkoutRequest request = CreateWorkoutRequest.builder()
                .title(title)
                .notes("Workout created for list endpoint test")
                .template(template)
                .build();

        return workoutService.createWorkout(workosId, request);
    }

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
        UUID exerciseId = UUID.randomUUID();
        exerciseRepository.save(Exercise.builder()
                .id(exerciseId)
                .name("Test Exercise")
                .build());
        CreateWorkoutTemplateRequest.TemplateExerciseRequest exercise = CreateWorkoutTemplateRequest.TemplateExerciseRequest.builder()
                .exerciseId(exerciseId)
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
        UUID exerciseId = UUID.randomUUID();
        exerciseRepository.save(Exercise.builder()
                .id(exerciseId)
                .name("Test Exercise")
                .build());
        CreateWorkoutTemplateRequest.TemplateExerciseRequest exercise = CreateWorkoutTemplateRequest.TemplateExerciseRequest.builder()
                .exerciseId(exerciseId)
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
                .andDo(print())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Push Day Workout"))
                .andExpect(jsonPath("$.title").value("Push Day Workout"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.templateId").isNotEmpty());

        // TODO: Validate the template is created successfully
    }

    @Test
    @DisplayName("GET /api/workouts -> 200 OK returns all workouts for authenticated user")
    void getWorkouts_success_returnsAllForUser(@TestUserContext String token, @TestUserContext com.services.active.models.user.User user) throws Exception {
        Workout workoutOne = createWorkout(user.getWorkosId(), "Push Day", UUID.randomUUID(), "Barbell Bench Press");
        Workout workoutTwo = createWorkout(user.getWorkosId(), "Pull Day", UUID.randomUUID(), "Pull-Up");

        mockMvc.perform(get("/api/workouts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        workoutOne.getId().toString(),
                        workoutTwo.getId().toString()
                )))
                .andExpect(jsonPath("$[*].title", hasItems("Push Day", "Pull Day")))
                .andExpect(jsonPath("$[*].createdAt", hasSize(2)))
                .andExpect(jsonPath("$[*].updatedAt", hasSize(2)))
                .andExpect(jsonPath("$[*].workoutTemplate.id", hasSize(2)))
                .andExpect(jsonPath("$[*].workoutTemplate.exercises[0].exerciseId", hasSize(2)))
                .andExpect(jsonPath("$[*].workoutTemplate.exercises[0].exerciseTitle", hasItems("Barbell Bench Press", "Pull-Up")))
                .andExpect(jsonPath("$[*].workoutTemplate.exercises[0].reps[0]", hasSize(2)))
                .andExpect(jsonPath("$[*].workoutTemplate.exercises[0].weight[0]", hasSize(2)));
    }
}
