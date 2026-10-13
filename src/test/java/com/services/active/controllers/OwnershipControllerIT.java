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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithTestUser
@SpringBootTest
@AutoConfigureMockMvc
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class OwnershipControllerIT extends IntegrationTestBase {
    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutService workoutService;

    private User owner;
    private User other;
    private String ownerToken;
    private String otherToken;
    private UUID exerciseId;
    private Workout ownWorkout;
    private Workout foreignWorkout;

    @BeforeEach
    void fixtures(@TestUserContext User user, @TestUserContext String token) {
        owner = user;
        ownerToken = token;
        other = userRepository.findByWorkosId("test_workos_ownership_other").orElseGet(() ->
                userRepository.save(User.builder().workosId("test_workos_ownership_other")
                        .createdAt(LocalDate.now()).timezone("UTC").build()));
        otherToken = token(other.getWorkosId());
        jdbcTemplate.update("""
                UPDATE users SET active_routine_id = NULL, current_streak = 0, longest_streak = 0,
                    next_workout_id = NULL, next_workout_deadline = NULL, last_workout_counted_date = NULL,
                    current_week_start = NULL, streak_freeze_count = 0 WHERE id IN (?, ?)
                """, owner.getId(), other.getId());
        exerciseId = UUID.randomUUID();
        exerciseRepository.save(Exercise.builder().id(exerciseId).name("Shared ownership exercise").build());
        ownWorkout = workout(owner, "Owner workout", "Owner template");
        foreignWorkout = workout(other, "Foreign workout", "Foreign template");
    }

    @ParameterizedTest
    @CsvSource({"foreign,false", "foreign,true", "missing,false", "missing,true"})
    void recordReferenceDeniedWithoutEffectsAndKeyCanBeReused(String reference, boolean keyed) throws Exception {
        UUID id = reference.equals("foreign") ? foreignWorkout.getId() : UUID.randomUUID();
        String key = keyed ? UUID.randomUUID().toString() : null;
        var before = userState();

        deny("POST", "/api/workouts/record", key, record(id), "Workout not found: " + id);

        for (String table : List.of("workout_records", "exercise_records", "exercise_personal_bests",
                "user_weekly_completed_workouts", "idempotency_keys")) {
            assertThat(count(table)).as(table).isZero();
        }
        assertThat(userState()).isEqualTo(before);
        request("POST", "/api/workouts/record", ownerToken, key, record(ownWorkout.getId()), 201)
                .andExpect(jsonPath("$.workoutRecord.workoutTitle").value("Owner workout"))
                .andExpect(jsonPath("$.workoutRecord.exerciseRecords[0].exerciseName").value("Shared ownership exercise"));
        assertThat(count("workout_records")).isEqualTo(1);
        assertThat(count("exercise_records")).isEqualTo(1);
        assertThat(count("exercise_personal_bests")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT current_streak FROM users WHERE id = ?",
                Integer.class, owner.getId())).isEqualTo(1);
        assertThat(count("idempotency_keys")).isEqualTo(keyed ? 1 : 0);
    }

    @ParameterizedTest
    @CsvSource({"mixed,false", "mixed,true", "rest,false", "rest,true", "missing,false", "missing,true"})
    void routineCreateDeniesEveryNonNullForeignOrMissingReference(String reference, boolean keyed) throws Exception {
        String key = keyed ? UUID.randomUUID().toString() : null;
        UUID active = routine(ownerToken, "Existing active", ownWorkout.getId(), true);
        var before = userState();
        var patterns = rows("routine_patterns");
        String body = "{\"name\":\"Denied create\",\"active\":true,\"pattern\":" + invalidPattern(reference) + "}";

        deny("POST", "/api/routines", key, body, "Workout not found");

        assertThat(count("routines")).isEqualTo(1);
        assertThat(rows("routine_patterns")).isEqualTo(patterns);
        assertThat(userState()).isEqualTo(before);
        assertThat(count("idempotency_keys")).isZero();
        assertThat(activeRoutine(owner)).isEqualTo(active);
        request("POST", "/api/routines", ownerToken, key,
                routineBody("Corrected create", ownWorkout.getId(), false), 201);
        assertThat(count("routines")).isEqualTo(2);
        assertThat(count("idempotency_keys")).isEqualTo(keyed ? 1 : 0);
    }

    @ParameterizedTest
    @CsvSource({"mixed,false", "mixed,true", "rest,false", "rest,true", "missing,false", "missing,true"})
    void routineReplacementDeniedBeforeRenamePatternOrActiveChanges(String reference, boolean keyed) throws Exception {
        UUID active = routine(ownerToken, "Existing active", ownWorkout.getId(), true);
        UUID target = routine(ownerToken, "Original", ownWorkout.getId(), false);
        String key = keyed ? UUID.randomUUID().toString() : null;
        var before = userState();
        var routines = rows("routines");
        var patterns = rows("routine_patterns");
        String body = "{\"name\":\"Renamed\",\"active\":true,\"routineType\":\"WEEKLY_COMPLETION\",\"pattern\":"
                + invalidPattern(reference) + "}";

        deny("PUT", "/api/routines/" + target, key, body, "Workout not found");

        assertThat(rows("routines")).isEqualTo(routines);
        assertThat(rows("routine_patterns")).isEqualTo(patterns);
        assertThat(userState()).isEqualTo(before);
        assertThat(activeRoutine(owner)).isEqualTo(active);
        assertThat(count("idempotency_keys")).isZero();
        request("PUT", "/api/routines/" + target, ownerToken, key,
                routineBody("Corrected replacement", ownWorkout.getId(), false), 200);
        assertThat(count("idempotency_keys")).isEqualTo(keyed ? 1 : 0);
        request("GET", "/api/routines/" + target, ownerToken, null, null, 200)
                .andExpect(jsonPath("$.name").value("Corrected replacement"))
                .andExpect(jsonPath("$.pattern[0].workoutId").value(ownWorkout.getId().toString()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ownedDuplicateAndNullReferencesCreateReplaceAndReplay(boolean keyed) throws Exception {
        String pattern = "[" + day(0, "WORKOUT", ownWorkout.getId()) + ","
                + day(1, "WORKOUT", ownWorkout.getId()) + "," + day(2, "REST", null)
                + "," + day(3, "WORKOUT", null) + "]";
        String key = keyed ? UUID.randomUUID().toString() : null;
        String body = "{\"name\":\"Valid routine\",\"pattern\":" + pattern + "}";
        String created = response("POST", "/api/routines", ownerToken, key, body, 201);
        UUID id = jdbcTemplate.queryForObject("SELECT id FROM routines WHERE name = 'Valid routine'", UUID.class);
        if (keyed) assertThat(response("POST", "/api/routines", ownerToken, key, body, 201)).isEqualTo(created);
        assertThat(count("routines")).isEqualTo(1);
        assertThat(count("routine_patterns")).isEqualTo(4);
        String updateKey = keyed ? UUID.randomUUID().toString() : null;
        String replacement = "{\"name\":\"Replaced\",\"active\":true,\"pattern\":["
                + day(0, "WORKOUT", ownWorkout.getId()) + "," + day(1, "REST", null) + "]}";
        String updated = response("PUT", "/api/routines/" + id, ownerToken, updateKey, replacement, 200);
        if (keyed) assertThat(response("PUT", "/api/routines/" + id, ownerToken, updateKey, replacement, 200)).isEqualTo(updated);
        request("GET", "/api/routines/" + id, ownerToken, null, null, 200)
                .andExpect(jsonPath("$.name").value("Replaced"))
                .andExpect(jsonPath("$.pattern.length()").value(2))
                .andExpect(jsonPath("$.pattern[0].workoutId").value(ownWorkout.getId().toString()))
                .andExpect(jsonPath("$.pattern[1].dayType").value("REST"));
        assertThat(activeRoutine(owner)).isEqualTo(id);
        assertThat(count("routine_patterns")).isEqualTo(2);
        assertThat(count("idempotency_keys")).isEqualTo(keyed ? 2 : 0);
        request("PUT", "/api/routines/" + id, ownerToken, null, "{\"pattern\":[]}", 400);
        request("PUT", "/api/routines/" + id, ownerToken, null, "{\"description\":\"Unchanged pattern\"}", 200);
        assertThat(count("routine_patterns")).isEqualTo(2);
    }

    @Test
    void receiptIsIsolatedAcrossOwnersAndRejectedOwnerCanReuseKey() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = record(ownWorkout.getId());
        String first = response("POST", "/api/workouts/record", ownerToken, key, body, 201);
        var receipt = rows("idempotency_keys");
        var before = userState();

        request("POST", "/api/workouts/record", otherToken, key, body, 404);
        request("POST", "/api/workouts/record", otherToken, key, body, 404);

        assertThat(rows("idempotency_keys")).isEqualTo(receipt);
        assertThat(userState()).isEqualTo(before);
        assertThat(response("POST", "/api/workouts/record", ownerToken, key, body, 201)).isEqualTo(first);
        assertThat(userState()).isEqualTo(before);
        assertThat(count("workout_records")).isEqualTo(1);
        assertThat(count("exercise_records")).isEqualTo(1);
        assertThat(count("exercise_personal_bests")).isEqualTo(1);
        request("POST", "/api/workouts/record", otherToken, key, record(foreignWorkout.getId()), 201)
                .andExpect(jsonPath("$.workoutRecord.workoutTitle").value("Foreign workout"));
        assertThat(count("workout_records")).isEqualTo(2);
        assertThat(count("idempotency_keys")).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"workout,PUT,false", "workout,PUT,true", "workout,DELETE,false", "workout,DELETE,true",
            "routine,PUT,false", "routine,PUT,true", "routine,DELETE,false", "routine,DELETE,true",
            "record,DELETE,false", "record,DELETE,true"})
    void foreignDirectMutationPreservesParentAndNestedState(String resource, String method, boolean keyed) throws Exception {
        UUID ownRoutine = routine(ownerToken, "Own active", ownWorkout.getId(), true);
        UUID foreignRoutine = routine(otherToken, "Foreign active", foreignWorkout.getId(), true);
        response("POST", "/api/workouts/record", otherToken, null, record(foreignWorkout.getId()), 201);
        UUID foreignRecord = jdbcTemplate.queryForObject("SELECT id FROM workout_records", UUID.class);
        String path = switch (resource) {
            case "workout" -> "/api/workouts/" + foreignWorkout.getId();
            case "routine" -> "/api/routines/" + foreignRoutine;
            default -> "/api/workouts/record/" + foreignRecord;
        };
        String body = resource.equals("workout")
                ? "{\"title\":\"Stolen\",\"template\":{\"exercises\":[]}}"
                : routineBody("Stolen", ownWorkout.getId(), true);
        var tables = List.of("workouts", "workout_templates", "template_exercises", "routines",
                "routine_patterns", "workout_records", "exercise_records", "exercise_personal_bests");
        var before = tables.stream().map(this::rows).toList();
        var users = userState();
        String key = keyed ? UUID.randomUUID().toString() : null;
        request(method, path, ownerToken, key, method.equals("PUT") ? body : null, 401);
        if (keyed) request(method, path, ownerToken, key, method.equals("PUT") ? body : null, 401);
        assertThat(tables.stream().map(this::rows).toList()).isEqualTo(before);
        assertThat(userState()).isEqualTo(users);
        assertThat(activeRoutine(owner)).isEqualTo(ownRoutine);
        assertThat(activeRoutine(other)).isEqualTo(foreignRoutine);
        assertThat(count("idempotency_keys")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"workout", "routine", "record"})
    void ownedDeleteReplaySucceedsAfterTargetIsGone(String resource) throws Exception {
        UUID routineId = routine(ownerToken, "Delete active", ownWorkout.getId(), true);
        response("POST", "/api/workouts/record", ownerToken, null, record(ownWorkout.getId()), 201);
        UUID recordId = jdbcTemplate.queryForObject("SELECT id FROM workout_records", UUID.class);
        String path = switch (resource) {
            case "workout" -> "/api/workouts/" + ownWorkout.getId();
            case "routine" -> "/api/routines/" + routineId;
            default -> "/api/workouts/record/" + recordId;
        };
        String key = UUID.randomUUID().toString();
        response("DELETE", path, ownerToken, key, null, 204);
        var before = List.of(rows("workouts"), rows("routines"), rows("routine_patterns"),
                rows("workout_records"), rows("exercise_records"));
        response("DELETE", path, ownerToken, key, null, 204);
        assertThat(List.of(rows("workouts"), rows("routines"), rows("routine_patterns"),
                rows("workout_records"), rows("exercise_records"))).isEqualTo(before);
        assertThat(count("idempotency_keys")).isEqualTo(1);
        switch (resource) {
            case "workout" -> {
                assertThat(count("workouts")).isEqualTo(1);
                assertThat(count("workout_templates")).isEqualTo(1);
                assertThat(count("template_exercises")).isEqualTo(1);
                request("GET", "/api/workouts/record", ownerToken, null, null, 200)
                        .andExpect(jsonPath("$[0].workoutTitle").value("Owner workout"));
            }
            case "routine" -> {
                assertThat(count("routines")).isZero();
                assertThat(count("routine_patterns")).isZero();
                assertThat(activeRoutine(owner)).isNull();
            }
            default -> {
                assertThat(count("workout_records")).isZero();
                assertThat(count("exercise_records")).isZero();
            }
        }
    }

    @Test
    void listsAndRoutineReadsContainOnlyOwnedParentsAndNestedData() throws Exception {
        UUID ownRoutine = routine(ownerToken, "Own active", ownWorkout.getId(), true);
        UUID foreignRoutine = routine(otherToken, "Foreign active", foreignWorkout.getId(), true);
        response("POST", "/api/workouts/record", ownerToken, null, record(ownWorkout.getId()), 201);
        response("POST", "/api/workouts/record", otherToken, null, record(foreignWorkout.getId()), 201);
        request("GET", "/api/workouts", ownerToken, null, null, 200)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ownWorkout.getId().toString()))
                .andExpect(jsonPath("$[0].title").value("Owner workout"))
                .andExpect(jsonPath("$[0].workoutTemplate.exercises[0].notes").value("Owner template"));
        request("GET", "/api/routines", ownerToken, null, null, 200)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ownRoutine.toString()))
                .andExpect(jsonPath("$[0].pattern[0].workoutId").value(ownWorkout.getId().toString()));
        request("GET", "/api/workouts/record", ownerToken, null, null, 200)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].workoutTitle").value("Owner workout"))
                .andExpect(jsonPath("$[0].exerciseRecords[0].reps[0]").value(5))
                .andExpect(jsonPath("$[0].exerciseRecords[0].exerciseName").value("Shared ownership exercise"));
        request("GET", "/api/routines/" + foreignRoutine, ownerToken, null, null, 404);
        request("GET", "/api/routines/active", ownerToken, null, null, 200)
                .andExpect(jsonPath("$.id").value(ownRoutine.toString()));
        jdbcTemplate.update("UPDATE users SET active_routine_id = ? WHERE id = ?", foreignRoutine, owner.getId());
        request("GET", "/api/routines/active", ownerToken, null, null, 404);
    }

    private Workout workout(User user, String title, String notes) {
        var exercise = CreateWorkoutTemplateRequest.TemplateExerciseRequest.builder()
                .exerciseId(exerciseId).reps(List.of(5)).weight(List.of(100.0)).notes(notes).build();
        return workoutService.createWorkout(user.getWorkosId(), CreateWorkoutRequest.builder().title(title)
                .template(CreateWorkoutTemplateRequest.builder().exercises(List.of(exercise)).build()).build());
    }

    private UUID routine(String token, String name, UUID workoutId, boolean active) throws Exception {
        request("POST", "/api/routines", token, null, routineBody(name, workoutId, active), 201);
        return jdbcTemplate.queryForObject("SELECT id FROM routines WHERE name = ?", UUID.class, name);
    }

    private String routineBody(String name, UUID workoutId, boolean active) {
        return "{\"name\":\"%s\",\"active\":%s,\"pattern\":[%s]}".formatted(name, active, day(0, "WORKOUT", workoutId));
    }

    private String invalidPattern(String reference) {
        UUID id = reference.equals("missing") ? UUID.randomUUID() : foreignWorkout.getId();
        return "[" + day(0, "WORKOUT", ownWorkout.getId()) + ","
                + day(1, reference.equals("rest") ? "REST" : "WORKOUT", id) + "]";
    }

    private String day(int index, String type, UUID workoutId) {
        return "{\"dayIndex\":%d,\"dayType\":\"%s\",\"workoutId\":%s}".formatted(
                index, type, workoutId == null ? "null" : "\"" + workoutId + "\"");
    }

    private String record(UUID workoutId) {
        return """
                {"workoutId":"%s","startTime":"%s","exerciseRecords":[{"exerciseId":"%s","reps":[5],"weight":[100.0]}]}
                """.formatted(workoutId, LocalDateTime.now().minusMinutes(10), exerciseId);
    }

    private void deny(String method, String path, String key, String body, String message) throws Exception {
        request(method, path, ownerToken, key, body, 404).andExpect(jsonPath("$.message").value(message));
        if (key != null) request(method, path, ownerToken, key, body, 404).andExpect(jsonPath("$.message").value(message));
    }

    private String response(String method, String path, String token, String key, String body, int expected) throws Exception {
        return request(method, path, token, key, body, expected).andReturn().getResponse().getContentAsString();
    }

    private ResultActions request(String method, String path, String token, String key, String body, int expected) throws Exception {
        var builder = MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), path)
                .header("Authorization", "Bearer " + token);
        if (key != null) builder.header("Idempotency-Key", key);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(builder).andExpect(status().is(expected));
    }

    private List<Map<String, Object>> userState() {
        return jdbcTemplate.queryForList("""
                SELECT id, active_routine_id, current_streak, longest_streak, next_workout_id,
                    next_workout_deadline, last_workout_counted_date, current_week_start, streak_freeze_count
                FROM users WHERE id IN (?, ?) ORDER BY id
                """, owner.getId(), other.getId());
    }

    private UUID activeRoutine(User user) {
        return jdbcTemplate.queryForObject("SELECT active_routine_id FROM users WHERE id = ?", UUID.class, user.getId());
    }

    private List<String> rows(String table) {
        return jdbcTemplate.queryForList("SELECT row_to_json(t)::text FROM " + table + " t ORDER BY 1", String.class);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private String token(String subject) {
        long now = System.currentTimeMillis() / 1000;
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"%s\",\"iat\":%d,\"exp\":%d}".formatted(subject, now, now + 3600).getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".test-signature";
    }
}
