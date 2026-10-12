package com.services.active.controllers;

import com.services.active.config.IntegrationTestBase;
import com.services.active.config.user.TestUserContext;
import com.services.active.config.user.WithTestUser;
import com.services.active.dto.CreateWorkoutRequest;
import com.services.active.dto.CreateWorkoutTemplateRequest;
import com.services.active.models.Exercise;
import com.services.active.models.Workout;
import com.services.active.models.user.User;
import com.services.active.repository.ExerciseRepository;
import com.services.active.repository.UserRepository;
import com.services.active.services.WorkoutService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithTestUser
@SpringBootTest
@AutoConfigureMockMvc
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class IdempotencyControllerIT extends IntegrationTestBase {
    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutService workoutService;

    @Test
    void createReplayReturnsOriginalResponseAndMutatesOnce(@TestUserContext String token) throws Exception {
        String key = UUID.randomUUID().toString();
        String request = """
                {"title":"Idempotent workout","notes":null,"template":{"exercises":[]}}
                """;

        String first = performPost("/api/workouts", token, key, request, 201).andReturn().getResponse().getContentAsString();
        String replay = performPost("/api/workouts", token, key, request, 201).andReturn().getResponse().getContentAsString();

        assertThat(replay).isEqualTo(first);
        assertThat(count("workouts")).isEqualTo(1);
        assertThat(count("workout_templates")).isEqualTo(1);
        assertThat(count("idempotency_keys")).isEqualTo(1);
    }

    @Test
    void deleteReplayReturnsNoContentAndMutatesOnce(
            @TestUserContext String token,
            @TestUserContext User user) throws Exception {
        Workout workout = workoutService.createWorkout(user.getWorkosId(), CreateWorkoutRequest.builder()
                .title("Delete once")
                .template(CreateWorkoutTemplateRequest.builder().exercises(List.of()).build())
                .build());
        String key = UUID.randomUUID().toString();

        performDelete("/api/workouts/" + workout.getId(), token, key);
        performDelete("/api/workouts/" + workout.getId(), token, key);

        assertThat(count("workouts")).isZero();
        assertThat(count("idempotency_keys")).isEqualTo(1);
    }

    @Test
    void changedBodyOrRouteConflictsAndCanonicalNullMatchesOmission(@TestUserContext String token) throws Exception {
        String bodyKey = UUID.randomUUID().toString();
        String original = """
                {"pattern":[{"dayType":"REST","dayIndex":0}],"name":"Canonical"}
                """;
        String sameDto = """
                {"name":"Canonical","description":null,"pattern":[{"dayIndex":0,"workoutId":null,"dayType":"REST"}],"active":null,"startDate":null,"routineType":null}
                """;
        String changed = """
                {"name":"Changed","pattern":[{"dayIndex":0,"dayType":"REST"}]}
                """;

        String first = performPost("/api/routines", token, bodyKey, original, 201).andReturn().getResponse().getContentAsString();
        assertThat(performPost("/api/routines", token, bodyKey, sameDto, 201)
                .andReturn().getResponse().getContentAsString()).isEqualTo(first);
        performPost("/api/routines", token, bodyKey, changed, 409)
                .andExpect(jsonPath("$.message").value("Idempotency key was already used for a different request"));

        String routeKey = UUID.randomUUID().toString();
        performPost("/api/routines", token, routeKey, original.replace("Canonical", "Route"), 201);
        performPost("/api/workouts", token, routeKey,
                "{\"title\":\"Route\",\"template\":{\"exercises\":[]}}", 409);
        assertThat(count("routines")).isEqualTo(2);
        assertThat(count("workouts")).isZero();
    }

    @Test
    void sameKeyIsIndependentForDifferentAuthenticatedOwners(@TestUserContext String token) throws Exception {
        User second = userRepository.findByWorkosId("test_workos_second_owner").orElseGet(() ->
                userRepository.save(User.builder()
                        .workosId("test_workos_second_owner")
                        .createdAt(LocalDate.now())
                        .timezone("UTC")
                        .build()));
        String secondToken = token(second.getWorkosId());
        String key = UUID.randomUUID().toString();
        String request = "{\"name\":\"Shared key\",\"pattern\":[{\"dayIndex\":0,\"dayType\":\"REST\"}]}";

        performPost("/api/routines", token, key, request, 201);
        performPost("/api/routines", secondToken, key, request, 201);

        assertThat(count("routines")).isEqualTo(2);
        assertThat(count("idempotency_keys")).isEqualTo(2);
    }

    @Test
    void concurrentRecordRetriesReturnOneResultAndOneSetOfSideEffects(
            @TestUserContext String token,
            @TestUserContext User user) throws Exception {
        UUID exerciseId = UUID.randomUUID();
        Workout workout = workout(user, exerciseId);
        resetStreak(user);
        String key = UUID.randomUUID().toString();
        String request = recordRequest(workout, exerciseId.toString());
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return performPost("/api/workouts/record", token, key, request, 201)
                        .andReturn().getResponse().getContentAsString();
            });
            var second = executor.submit(() -> {
                start.await();
                return performPost("/api/workouts/record", token, key, request, 201)
                        .andReturn().getResponse().getContentAsString();
            });
            start.countDown();

            assertThat(first.get()).isEqualTo(second.get());
        }

        assertThat(count("workout_records")).isEqualTo(1);
        assertThat(count("exercise_records")).isEqualTo(1);
        assertThat(count("exercise_personal_bests")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_streak FROM users WHERE workos_id = ?", Integer.class, user.getWorkosId())).isEqualTo(1);
    }

    @Test
    void failedMutationRollsBackClaimAndDomainDataSoRetryCanExecute(
            @TestUserContext String token,
            @TestUserContext User user) throws Exception {
        UUID exerciseId = UUID.randomUUID();
        Workout workout = workout(user, exerciseId);
        resetStreak(user);
        String key = UUID.randomUUID().toString();

        performPost("/api/workouts/record", token, key, recordRequest(workout, "not-a-uuid"), 500);
        assertThat(count("workout_records")).isZero();
        assertThat(count("idempotency_keys")).isZero();

        performPost("/api/workouts/record", token, key, recordRequest(workout, exerciseId.toString()), 201);
        assertThat(count("workout_records")).isEqualTo(1);
        assertThat(count("idempotency_keys")).isEqualTo(1);
    }

    @Test
    void invalidKeyLengthIsRejectedBeforeMutation(@TestUserContext String token) throws Exception {
        String request = "{\"title\":\"Invalid key\",\"template\":{\"exercises\":[]}}";
        performPost("/api/workouts", token, "", request, 400);
        performPost("/api/workouts", token, "x".repeat(256), request, 400);
        assertThat(count("workouts")).isZero();
    }

    private Workout workout(User user, UUID exerciseId) {
        exerciseRepository.save(Exercise.builder().id(exerciseId).name("Idempotency exercise").build());
        var exercise = CreateWorkoutTemplateRequest.TemplateExerciseRequest.builder()
                .exerciseId(exerciseId)
                .reps(List.of(5))
                .weight(List.of(100.0))
                .build();
        return workoutService.createWorkout(user.getWorkosId(), CreateWorkoutRequest.builder()
                .title("Record workout")
                .template(CreateWorkoutTemplateRequest.builder().exercises(List.of(exercise)).build())
                .build());
    }

    private String recordRequest(Workout workout, String exerciseId) {
        return """
                {"workoutId":"%s","startTime":"%s","exerciseRecords":[{"exerciseId":"%s","reps":[5],"weight":[100.0]}]}
                """.formatted(workout.getId(), LocalDateTime.now().minusMinutes(10), exerciseId);
    }

    private void resetStreak(User user) {
        jdbcTemplate.update("""
                UPDATE users SET current_streak = 0, longest_streak = 0, next_workout_id = NULL,
                    next_workout_deadline = NULL, last_workout_counted_date = NULL
                WHERE id = ?
                """, user.getId());
    }

    private org.springframework.test.web.servlet.ResultActions performPost(
            String path, String token, String key, String body, int status) throws Exception {
        return mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(status));
    }

    private void performDelete(String path, String token, String key) throws Exception {
        mockMvc.perform(delete(path)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key))
                .andExpect(status().isNoContent());
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private String token(String owner) {
        long now = System.currentTimeMillis() / 1000;
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"%s\",\"iat\":%d,\"exp\":%d}".formatted(owner, now, now + 3600))
                        .getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".test-signature";
    }
}
