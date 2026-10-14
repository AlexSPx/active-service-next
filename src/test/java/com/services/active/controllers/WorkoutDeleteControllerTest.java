package com.services.active.controllers;

import com.services.active.config.IntegrationTestBase;
import com.services.active.config.user.TestUserContext;
import com.services.active.config.user.WithTestUser;
import com.services.active.dto.CreateWorkoutRequest;
import com.services.active.dto.CreateWorkoutTemplateRequest;
import com.services.active.dto.WorkoutRecordRequest;
import com.services.active.dto.CreateRoutineRequest;
import com.services.active.dto.RoutinePatternRequest;
import com.services.active.models.types.DayType;
import com.services.active.models.Exercise;
import com.services.active.models.TemplateExercise;
import com.services.active.models.user.User;
import com.services.active.models.Workout;
import com.services.active.models.WorkoutRecord;
import com.services.active.repository.ExerciseRepository;
import com.services.active.repository.WorkoutRecordRepository;
import com.services.active.repository.WorkoutRepository;
import com.services.active.repository.WorkoutTemplateRepository;
import com.services.active.repository.RoutineRepository;
import com.services.active.services.WorkoutRecordService;
import com.services.active.services.WorkoutService;
import com.services.active.services.RoutineService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WithTestUser
@SpringBootTest
@AutoConfigureMockMvc
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class WorkoutDeleteControllerTest extends IntegrationTestBase {

    private final MockMvc mockMvc;
    private final WorkoutService workoutService;
    private final WorkoutRecordService workoutRecordService;
    private final WorkoutRepository workoutRepository;
    private final WorkoutTemplateRepository workoutTemplateRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final ExerciseRepository exerciseRepository;
    private final JsonMapper jsonMapper;
    private final JdbcTemplate jdbcTemplate;
    private final RoutineService routineService;
    private final RoutineRepository routineRepository;

    private Workout createWorkout(User user, UUID exerciseId) {
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
                .title("Delete Me Workout")
                .notes("To be deleted in test")
                .template(template)
                .build();

        return workoutService.createWorkout(user.getWorkosId(), request);
    }

    @Test
    @DisplayName("DELETE /api/workouts/{id} -> 204 NO CONTENT deletes workout and template but keeps records")
    void deleteWorkout_deletesWorkoutAndTemplate_keepsRecords(@TestUserContext String token, @TestUserContext User user) throws Exception {
        // Test Hibernate's create-drop schema omits this scalar FK; production V1 defines ON DELETE SET NULL.
        jdbcTemplate.execute("ALTER TABLE workout_records ADD CONSTRAINT fk_test_workout_record_workout "
                + "FOREIGN KEY (workout_id) REFERENCES workouts(id) ON DELETE SET NULL");
        UUID exerciseId = UUID.randomUUID();
        // Create workout
        Workout workout = createWorkout(user, exerciseId);
        UUID templateId = workout.getTemplateId();

        // Create a record for this workout
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(30);
        WorkoutRecordRequest.ExerciseRecord ex = new WorkoutRecordRequest.ExerciseRecord(
                exerciseId.toString(),
                List.of(10, 8, 6),
                List.of(50.0, 55.0, 60.0),
                null,
                null
        );
        WorkoutRecordRequest recordReq = new WorkoutRecordRequest();
        recordReq.setWorkoutId(workout.getId().toString());
        recordReq.setNotes("Record to survive delete");
        recordReq.setStartTime(startTime);
        recordReq.setExerciseRecords(List.of(ex));
        com.services.active.dto.WorkoutRecordCreateResponse created = workoutRecordService.createWorkoutRecord(user.getWorkosId(), recordReq);
        UUID recordId = UUID.fromString(created.getWorkoutRecord().getId());

        // Sanity: record exists
        WorkoutRecord beforeDelete = workoutRecordRepository.findById(recordId).orElse(null);
        assertThat(beforeDelete).isNotNull();
        assertThat(beforeDelete.getWorkoutId()).isEqualTo(workout.getId());
        assertThat(beforeDelete.getWorkoutTitle()).isEqualTo("Delete Me Workout");

        // Delete the workout
        mockMvc.perform(delete("/api/workouts/" + workout.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Workout deleted
        assertThat(workoutRepository.findById(workout.getId())).isEmpty();
        // Template deleted
        if (templateId != null) {
            assertThat(workoutTemplateRepository.findById(templateId)).isEmpty();
        }
        // Record still exists and retains title snapshot
        WorkoutRecord afterDelete = workoutRecordRepository.findById(recordId).orElse(null);
        assertThat(afterDelete).isNotNull();
        assertThat(afterDelete.getWorkoutTitle()).isEqualTo("Delete Me Workout");
        assertThat(afterDelete.getStartTime()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT workout_id FROM workout_records WHERE id = ?", UUID.class, recordId)).isNull();

        String historyJson = mockMvc.perform(get("/api/workouts/record")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode historyRecord = null;
        for (JsonNode item : jsonMapper.readTree(historyJson)) {
            if (recordId.toString().equals(item.get("id").asText())) {
                historyRecord = item;
                break;
            }
        }
        assertThat(historyRecord).isNotNull();
        assertThat(historyRecord.get("workoutId").isNull()).isTrue();
        assertThat(historyRecord.get("workoutTitle").asText()).isEqualTo("Delete Me Workout");
        assertThat(historyRecord.get("notes").asText()).isEqualTo("Record to survive delete");
        assertThat(LocalDateTime.parse(historyRecord.get("startTime").asText()))
                .isEqualTo(afterDelete.getStartTime());
        JsonNode exerciseRecord = historyRecord.get("exerciseRecords").get(0);
        assertThat(exerciseRecord.get("exerciseName").asText()).isEqualTo("Test Exercise");
        assertThat(exerciseRecord.get("reps").get(0).asInt()).isEqualTo(10);
        assertThat(exerciseRecord.get("weight").get(0).asDouble()).isEqualTo(50.0);
    }

    @Test
    void deleteWorkout_versionedResponseCarriesAffectedRoutineVersion(
            @TestUserContext String token,
            @TestUserContext User user) throws Exception {
        jdbcTemplate.execute("ALTER TABLE routine_patterns ADD CONSTRAINT fk_test_routine_pattern_workout "
                + "FOREIGN KEY (workout_id) REFERENCES workouts(id) ON DELETE SET NULL");
        Workout workout = createWorkout(user, UUID.randomUUID());
        var routine = routineService.createRoutine(user.getWorkosId(), CreateRoutineRequest.builder()
                .name("Uses deleted workout")
                .pattern(List.of(RoutinePatternRequest.builder()
                        .dayIndex(0)
                        .dayType(DayType.WORKOUT)
                        .workoutId(workout.getId())
                        .build()))
                .active(false)
                .build());

        mockMvc.perform(delete("/api/workouts/" + workout.getId())
                        .queryParam("baseVersion", workout.getVersion().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedWorkoutId").value(workout.getId().toString()))
                .andExpect(jsonPath("$.routineVersions[0].id").value(routine.getId().toString()))
                .andExpect(jsonPath("$.routineVersions[0].version").value(routine.getVersion() + 1));

        assertThat(routineRepository.findById(routine.getId()).orElseThrow().getVersion())
                .isEqualTo(routine.getVersion() + 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT workout_id FROM routine_patterns WHERE routine_id = ?",
                UUID.class,
                routine.getId())).isNull();
    }
}
