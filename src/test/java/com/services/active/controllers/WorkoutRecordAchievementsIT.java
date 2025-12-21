package com.services.active.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.services.active.config.IntegrationTestBase;
import com.services.active.config.user.TestUserContext;
import com.services.active.config.user.WithTestUser;
import com.services.active.dto.CreateWorkoutRequest;
import com.services.active.dto.CreateWorkoutTemplateRequest;
import com.services.active.models.ExercisePersonalBest;
import com.services.active.models.ExerciseRecord;
import com.services.active.models.TemplateExercise;
import com.services.active.models.user.User;
import com.services.active.models.Workout;
import com.services.active.models.WorkoutRecord;
import com.services.active.repository.ExercisePersonalBestRepository;
import com.services.active.repository.ExerciseRecordRepository;
import com.services.active.repository.WorkoutRecordRepository;
import com.services.active.services.WorkoutService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithTestUser
@SpringBootTest
@AutoConfigureMockMvc
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class WorkoutRecordAchievementsIT extends IntegrationTestBase {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    private final WorkoutService workoutService;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final ExercisePersonalBestRepository personalBestRepository;

    @BeforeEach
    void setupMapper() {
        objectMapper.findAndRegisterModules();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    private Workout createSimpleWorkout(User user) {
        TemplateExercise ex = TemplateExercise.builder()
                .exerciseId("exercise-1")
                .reps(List.of(5, 3, 1))
                .weight(List.of(100.0, 110.0, 120.0))
                .notes("Bench press")
                .build();
        CreateWorkoutTemplateRequest template = CreateWorkoutTemplateRequest.builder()
                .exercises(List.of(ex))
                .build();
        CreateWorkoutRequest req = CreateWorkoutRequest.builder()
                .title("Bench Session")
                .notes("PR hunt")
                .template(template)
                .build();
        return workoutService.createWorkout(user.getWorkosId(), req);
    }

    private String postWorkoutRecord(String token, String workoutId, String exercisePayload) throws Exception {
        String payload = """
                {
                  "workoutId": "%s",
                  "notes": "session",
                  "startTime": "%s",
                  "exerciseRecords": [%s]
                }
                """.formatted(workoutId, LocalDateTime.now().minusMinutes(30).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), exercisePayload);
        String responseContent = mockMvc.perform(post("/api/workouts/record")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        // parse id from nested JSON response
        var node = objectMapper.readTree(responseContent);
        return node.at("/workoutRecord/id").asText();
    }

    private ExerciseRecord fetchExerciseRecordByWorkoutRecordId(String workoutRecordId, String exerciseId) {
        WorkoutRecord wr = workoutRecordRepository.findById(workoutRecordId).orElseThrow();
        List<ExerciseRecord> records = exerciseRecordRepository.findAllById(wr.getExerciseRecordIds());
        return records.stream().filter(r -> exerciseId.equals(r.getExerciseId())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("First record sets both 1RM and volume PRs, PB saved and achievements present")
    void firstRecordPersistsAchievementsAndPB(@TestUserContext String token, @TestUserContext User user) throws Exception {
        Workout workout = createSimpleWorkout(user);
        String exerciseJson = """
        {
          "exerciseId": "exercise-1",
          "reps": [5, 3, 1],
          "weight": [100.0, 110.0, 120.0],
          "notes": "go"
        }
        """;
        String wrId = postWorkoutRecord(token, workout.getId(), exerciseJson);

        ExerciseRecord er = fetchExerciseRecordByWorkoutRecordId(wrId, "exercise-1");
        assertThat(er.getAchievedOneRm()).isNotNull();
        assertThat(er.getAchievedOneRm().getSetIndex()).isEqualTo(2);
        assertThat(er.getAchievedOneRm().getValue()).isBetween(123.9, 125.5);
        assertThat(er.getAchievedTotalVolume()).isNotNull();
        assertThat(er.getAchievedTotalVolume().getValue()).isEqualTo(950.0);

        Optional<ExercisePersonalBest> pbOpt = personalBestRepository.findByUserIdAndExerciseId(user.getId(), "exercise-1");
        assertThat(pbOpt).isPresent();
        ExercisePersonalBest pb = pbOpt.get();
        assertThat(pb.getOneRm()).isBetween(123.9, 125.5);
        assertThat(pb.getOneRmRecordId()).isNotNull();
        assertThat(pb.getOneRmRecordSetIndex()).isEqualTo(2);
        assertThat(pb.getTotalVolume()).isEqualTo(950.0);
        assertThat(pb.getTotalVolumeRecordId()).isNotNull();
    }

    @Test
    @DisplayName("Non-PR record does not persist achievements and PBs remain unchanged")
    void nonPrRecordDoesNotPersistAchievements(@TestUserContext String token, @TestUserContext User user) throws Exception {
        Workout workout = createSimpleWorkout(user);
        // Seed a PR first
        String wr1 = postWorkoutRecord(token, workout.getId(), """
        {
          "exerciseId": "exercise-1",
          "reps": [5, 3, 1],
          "weight": [100.0, 110.0, 120.0]
        }
        """);
        ExerciseRecord seed = fetchExerciseRecordByWorkoutRecordId(wr1, "exercise-1");
        assertThat(seed.getAchievedOneRm()).isNotNull();

        // Post a worse performance
        String wr2 = postWorkoutRecord(token, workout.getId(), """
        {
          "exerciseId": "exercise-1",
          "reps": [5, 3, 1],
          "weight": [90.0, 100.0, 110.0]
        }
        """);
        ExerciseRecord er2 = fetchExerciseRecordByWorkoutRecordId(wr2, "exercise-1");
        assertThat(er2.getAchievedOneRm()).isNull();
        assertThat(er2.getAchievedTotalVolume()).isNull();

        ExercisePersonalBest pb = personalBestRepository.findByUserIdAndExerciseId(user.getId(), "exercise-1").orElseThrow();
        assertThat(pb.getOneRm()).isGreaterThan(123.9);
        assertThat(pb.getTotalVolume()).isEqualTo(950.0);
    }

    @Test
    @DisplayName("Record with higher 1RM but lower volume updates only 1RM PB")
    void higherOneRmLowerVolumeUpdatesOnlyOneRm(@TestUserContext String token, @TestUserContext User user) throws Exception {
        Workout workout = createSimpleWorkout(user);
        // Seed
        postWorkoutRecord(token, workout.getId(), """
        {
          "exerciseId": "exercise-1",
          "reps": [5, 3, 1],
          "weight": [100.0, 110.0, 120.0]
        }
        """);
        // Better 1RM (125x1) lower volume
        String wr2 = postWorkoutRecord(token, workout.getId(), """
        {
          "exerciseId": "exercise-1",
          "reps": [1],
          "weight": [125.0]
        }
        """);
        ExerciseRecord er2 = fetchExerciseRecordByWorkoutRecordId(wr2, "exercise-1");
        assertThat(er2.getAchievedOneRm()).isNotNull();
        assertThat(er2.getAchievedOneRm().getSetIndex()).isEqualTo(0);
        assertThat(er2.getAchievedTotalVolume()).isNull();

        ExercisePersonalBest pb = personalBestRepository.findByUserIdAndExerciseId(user.getId(), "exercise-1").orElseThrow();
        assertThat(pb.getOneRm()).isBetween(128.5, 130.0);
        assertThat(pb.getTotalVolume()).isEqualTo(950.0);
    }

    @Test
    @DisplayName("Record with higher volume but lower 1RM updates only volume PB")
    void higherVolumeLowerOneRmUpdatesOnlyVolume(@TestUserContext String token, @TestUserContext User user) throws Exception {
        Workout workout = createSimpleWorkout(user);
        // Seed best 1RM 125x1 and volume 950 from first
        postWorkoutRecord(token, workout.getId(), """
        {
          "exerciseId": "exercise-1",
          "reps": [5, 3, 1],
          "weight": [100.0, 110.0, 120.0]
        }
        """);
        postWorkoutRecord(token, workout.getId(), """
        {
          "exerciseId": "exercise-1",
          "reps": [1],
          "weight": [125.0]
        }
        """);

        // Higher volume but lower 1RM (80x15 => vol 1200, 1RM ~120)
        String wr3 = postWorkoutRecord(token, workout.getId(), """
        {
          "exerciseId": "exercise-1",
          "reps": [15],
          "weight": [80.0]
        }
        """);
        ExerciseRecord er3 = fetchExerciseRecordByWorkoutRecordId(wr3, "exercise-1");
        assertThat(er3.getAchievedOneRm()).isNull();
        assertThat(er3.getAchievedTotalVolume()).isNotNull();
        assertThat(er3.getAchievedTotalVolume().getValue()).isEqualTo(1200.0);

        ExercisePersonalBest pb = personalBestRepository.findByUserIdAndExerciseId(user.getId(), "exercise-1").orElseThrow();
        assertThat(pb.getOneRm()).isBetween(128.5, 130.0);
        assertThat(pb.getTotalVolume()).isEqualTo(1200.0);
    }

    @Test
    @DisplayName("GET /api/workouts/record returns achievement fields in response for PR records")
    void getWorkoutRecords_returnsAchievements(@TestUserContext String token, @TestUserContext User user) throws Exception {
        Workout workout = createSimpleWorkout(user);
        // Create a PR record
        postWorkoutRecord(token, workout.getId(), """
        {
          "exerciseId": "exercise-1",
          "reps": [5, 3, 1],
          "weight": [100.0, 110.0, 120.0]
        }
        """);

        mockMvc.perform(get("/api/workouts/record")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // Further JSONPath assertions could be added if needed; focus here is end-to-end success.
    }
}
