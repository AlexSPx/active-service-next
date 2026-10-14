package com.services.active.controllers;

import com.services.active.config.IntegrationTestBase;
import com.services.active.config.user.TestUserContext;
import com.services.active.config.user.WithTestUser;
import com.services.active.dto.CreateWorkoutRequest;
import com.services.active.dto.CreateWorkoutTemplateRequest;
import com.services.active.dto.CreateRoutineRequest;
import com.services.active.models.Exercise;
import com.services.active.models.Routine;
import com.services.active.models.Workout;
import com.services.active.models.user.User;
import com.services.active.repository.ExerciseRepository;
import com.services.active.repository.RoutineRepository;
import com.services.active.repository.UserRepository;
import com.services.active.services.RoutineService;
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
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Base64;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;

import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private final RoutineService routineService;
    private final RoutineRepository routineRepository;
    private final JsonMapper jsonMapper;

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
    void concurrentWorkoutEditsAcceptOneVersionAndRollbackTheStaleRequest(
            @TestUserContext String token,
            @TestUserContext User user) throws Exception {
        UUID firstExercise = UUID.randomUUID();
        UUID secondExercise = UUID.randomUUID();
        exerciseRepository.save(Exercise.builder().id(firstExercise).name("Workout race first").build());
        exerciseRepository.save(Exercise.builder().id(secondExercise).name("Workout race second").build());
        Workout workout = workoutService.createWorkout(user.getWorkosId(), CreateWorkoutRequest.builder()
                .title("Workout race base")
                .template(CreateWorkoutTemplateRequest.builder().exercises(List.of()).build())
                .build());
        long profileVersion = currentProfileVersion(user.getId());
        String firstKey = UUID.randomUUID().toString();
        String secondKey = UUID.randomUUID().toString();
        String path = "/api/workouts/" + workout.getId();
        String firstBody = workoutUpdateBody("Workout race first", firstExercise);
        String secondBody = workoutUpdateBody("Workout race second", secondExercise);

        List<ConcurrentResponse> responses = race(
                () -> performPut(path, token, firstKey, firstBody),
                () -> performPut(path, token, secondKey, secondBody));

        assertOneAcceptedAndOneStale(responses, 1);
        ConcurrentResponse winner = accepted(responses);
        String winningTitle = jsonMapper.readTree(winner.body()).get("title").asText();
        UUID winningExercise = winningTitle.endsWith("first") ? firstExercise : secondExercise;
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM workouts WHERE id = ?", String.class, workout.getId()))
                .isEqualTo(winningTitle);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM workouts WHERE id = ?", Long.class, workout.getId())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForList(
                "SELECT exercise_id FROM template_exercises WHERE template_id = ?", UUID.class, workout.getTemplateId()))
                .containsExactly(winningExercise);
        assertThat(currentProfileVersion(user.getId())).isEqualTo(profileVersion);
        assertOnlyWinnerReceipt(firstKey, secondKey, winner.body());
    }

    @Test
    void concurrentRoutineEditsAcceptOneVersionAndRollbackTheStaleRequest(
            @TestUserContext String token,
            @TestUserContext User user) throws Exception {
        Workout firstWorkout = workoutService.createWorkout(user.getWorkosId(), CreateWorkoutRequest.builder()
                .title("Routine race workout first")
                .template(CreateWorkoutTemplateRequest.builder().exercises(List.of()).build())
                .build());
        Workout secondWorkout = workoutService.createWorkout(user.getWorkosId(), CreateWorkoutRequest.builder()
                .title("Routine race workout second")
                .template(CreateWorkoutTemplateRequest.builder().exercises(List.of()).build())
                .build());
        Routine routine = routineRepository.findById(routineService.createRoutine(
                user.getWorkosId(),
                CreateRoutineRequest.builder().name("Routine race base").build()).getId()).orElseThrow();
        long profileVersion = currentProfileVersion(user.getId());
        String firstKey = UUID.randomUUID().toString();
        String secondKey = UUID.randomUUID().toString();
        String path = "/api/routines/" + routine.getId();
        String firstBody = routineUpdateBody("Routine race first", firstWorkout.getId());
        String secondBody = routineUpdateBody("Routine race second", secondWorkout.getId());

        List<ConcurrentResponse> responses = race(
                () -> performPut(path, token, firstKey, firstBody),
                () -> performPut(path, token, secondKey, secondBody));

        assertOneAcceptedAndOneStale(responses, 1);
        ConcurrentResponse winner = accepted(responses);
        String winningName = jsonMapper.readTree(winner.body()).get("name").asText();
        UUID winningWorkout = winningName.endsWith("first") ? firstWorkout.getId() : secondWorkout.getId();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM routines WHERE id = ?", String.class, routine.getId()))
                .isEqualTo(winningName);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM routines WHERE id = ?", Long.class, routine.getId())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForList(
                "SELECT workout_id FROM routine_patterns WHERE routine_id = ?", UUID.class, routine.getId()))
                .containsExactly(winningWorkout);
        assertThat(currentProfileVersion(user.getId())).isEqualTo(profileVersion);
        assertOnlyWinnerReceipt(firstKey, secondKey, winner.body());
    }

    @Test
    void concurrentProfileEditsAcceptOneVersionAndRollbackTheStaleRequest(
            @TestUserContext String token,
            @TestUserContext User user) throws Exception {
        long baseVersion = currentProfileVersion(user.getId());
        long receiptsBefore = count("idempotency_keys");
        String firstBody = profileUpdateBody(baseVersion, "Europe/Paris", 71.0, 1);
        String secondBody = profileUpdateBody(baseVersion, "Europe/Berlin", 82.0, 3);

        List<ConcurrentResponse> responses = race(
                () -> performPatch("/api/user/me", token, firstBody),
                () -> performPatch("/api/user/me", token, secondBody));

        assertOneAcceptedAndOneStale(responses, baseVersion + 1);
        ConcurrentResponse winner = accepted(responses);
        var winnerBody = jsonMapper.readTree(winner.body());
        String winningTimezone = winnerBody.get("timezone").asText();
        double winningWeight = winningTimezone.equals("Europe/Paris") ? 71.0 : 82.0;
        int winningFrequency = winningTimezone.equals("Europe/Paris") ? 1 : 3;
        assertThat(jdbcTemplate.queryForObject(
                "SELECT timezone FROM users WHERE id = ?", String.class, user.getId()))
                .isEqualTo(winningTimezone);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT weight_kg FROM users WHERE id = ?", Double.class, user.getId()))
                .isEqualTo(winningWeight);
        assertThat(currentProfileVersion(user.getId())).isEqualTo(baseVersion + 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_notification_schedule WHERE user_id = ?", Long.class, user.getId()))
                .isEqualTo((long) winningFrequency);
        assertThat(count("idempotency_keys")).isEqualTo(receiptsBefore);
    }

    @Test
    void negativeBaseVersionsReturnStaleWithoutStateOrReceipts(
            @TestUserContext String token,
            @TestUserContext User user) throws Exception {
        Workout workout = workoutService.createWorkout(user.getWorkosId(), CreateWorkoutRequest.builder()
                .title("Negative base workout")
                .template(CreateWorkoutTemplateRequest.builder().exercises(List.of()).build())
                .build());
        Routine routine = routineRepository.findById(routineService.createRoutine(
                user.getWorkosId(),
                CreateRoutineRequest.builder().name("Negative base routine").build()).getId()).orElseThrow();
        long profileVersion = currentProfileVersion(user.getId());
        String workoutKey = UUID.randomUUID().toString();
        String routineKey = UUID.randomUUID().toString();

        mockMvc.perform(put("/api/workouts/{id}", workout.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", workoutKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseVersion\":-1,\"title\":\"Rejected workout\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"))
                .andExpect(jsonPath("$.currentVersion").value(0));
        mockMvc.perform(put("/api/routines/{id}", routine.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", routineKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseVersion\":-1,\"name\":\"Rejected routine\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"))
                .andExpect(jsonPath("$.currentVersion").value(0));
        mockMvc.perform(patch("/api/user/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseVersion\":-1,\"timezone\":\"Europe/Paris\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"))
                .andExpect(jsonPath("$.currentVersion").value(profileVersion));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM workouts WHERE id = ?", String.class, workout.getId()))
                .isEqualTo("Negative base workout");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM routines WHERE id = ?", String.class, routine.getId()))
                .isEqualTo("Negative base routine");
        assertThat(currentProfileVersion(user.getId())).isEqualTo(profileVersion);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM idempotency_keys WHERE operation_id IN (?, ?)",
                Long.class,
                workoutKey,
                routineKey)).isZero();
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

    @Test
    void historicalReceiptFingerprintKeepsOldNullsAndMissingVersionDoesNotBecomeZero(
            @TestUserContext String token,
            @TestUserContext User user) throws Exception {
        UUID historicalId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        String path = "/api/workouts/" + historicalId;
        String canonicalOldRequest = "{\"notes\":null,\"template\":null,\"title\":\"Historical\"}";
        String response = """
                {"id":"%s","title":"Historical","notes":null,"createdAt":null,"updatedAt":null,"userId":null,"templateId":null}
                """.formatted(historicalId).trim();
        jdbcTemplate.update("""
                INSERT INTO idempotency_keys (owner_id, operation_id, fingerprint, response_body, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                user.getWorkosId(),
                key,
                fingerprint("PUT", path, canonicalOldRequest),
                response,
                Timestamp.from(Instant.now()));

        String replay = mockMvc.perform(put(path)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Historical\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(replay).isEqualTo(response);
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

    private String workoutUpdateBody(String title, UUID exerciseId) {
        return """
                {"baseVersion":0,"title":"%s","template":{"exercises":[{"exerciseId":"%s","reps":[5]}]}}
                """.formatted(title, exerciseId).trim();
    }

    private String routineUpdateBody(String name, UUID workoutId) {
        return """
                {"baseVersion":0,"name":"%s","pattern":[{"dayIndex":0,"dayType":"WORKOUT","workoutId":"%s"}]}
                """.formatted(name, workoutId).trim();
    }

    private String profileUpdateBody(long baseVersion, String timezone, double weight, int notificationFrequency) {
        return """
                {"baseVersion":%d,"timezone":"%s","notificationFrequency":%d,"measurements":{"weightKg":%s}}
                """.formatted(baseVersion, timezone, notificationFrequency, weight).trim();
    }

    private MvcResult performPut(String path, String token, String key, String body) throws Exception {
        return mockMvc.perform(put(path)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private MvcResult performPatch(String path, String token, String body) throws Exception {
        return mockMvc.perform(patch(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private List<ConcurrentResponse> race(Callable<MvcResult> first, Callable<MvcResult> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstFuture = executor.submit(() -> {
                start.await();
                return first.call();
            });
            var secondFuture = executor.submit(() -> {
                start.await();
                return second.call();
            });
            start.countDown();
            return List.of(response(firstFuture.get()), response(secondFuture.get()));
        }
    }

    private ConcurrentResponse response(MvcResult result) throws Exception {
        return new ConcurrentResponse(result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    private void assertOneAcceptedAndOneStale(List<ConcurrentResponse> responses, long currentVersion) {
        assertThat(responses).extracting(ConcurrentResponse::status).containsExactlyInAnyOrder(200, 409);
        assertThat(jsonMapper.readTree(stale(responses).body()).get("code").asText()).isEqualTo("STALE_VERSION");
        assertThat(jsonMapper.readTree(stale(responses).body()).get("currentVersion").asLong())
                .isEqualTo(currentVersion);
    }

    private ConcurrentResponse accepted(List<ConcurrentResponse> responses) {
        return responses.stream().filter(response -> response.status() == 200).findFirst().orElseThrow();
    }

    private ConcurrentResponse stale(List<ConcurrentResponse> responses) {
        return responses.stream().filter(response -> response.status() == 409).findFirst().orElseThrow();
    }

    private long currentProfileVersion(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT profile_version FROM users WHERE id = ?", Long.class, userId);
    }

    private void assertOnlyWinnerReceipt(String firstKey, String secondKey, String winnerBody) {
        List<String> storedResponses = jdbcTemplate.queryForList(
                "SELECT response_body FROM idempotency_keys WHERE operation_id IN (?, ?)",
                String.class,
                firstKey,
                secondKey);
        assertThat(storedResponses).containsExactly(winnerBody);
    }

    private record ConcurrentResponse(int status, String body) {
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

    private String fingerprint(String method, String path, String canonicalRequest) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                (method + "\n" + path + "\n" + canonicalRequest).getBytes(StandardCharsets.UTF_8)));
    }
}
