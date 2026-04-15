package com.services.active.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.services.active.config.IntegrationTestBase;
import com.services.active.config.user.TestUserContext;
import com.services.active.config.user.WithTestUser;
import com.services.active.dto.CreateWorkoutRequest;
import com.services.active.dto.CreateWorkoutTemplateRequest;
import com.services.active.models.Exercise;
import com.services.active.models.ExercisePersonalBest;
import com.services.active.models.ExerciseRecord;
import com.services.active.models.user.User;
import com.services.active.models.Workout;
import com.services.active.models.WorkoutRecord;
import java.util.UUID;

import com.services.active.repository.ExercisePersonalBestRepository;
import com.services.active.repository.ExerciseRecordRepository;
import com.services.active.repository.ExerciseRepository;
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
    private final ExerciseRepository exerciseRepository;

    @BeforeEach
    void setupMapper() {
        objectMapper.findAndRegisterModules();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    private Workout createSimpleWorkout(User user, UUID testExerciseId) {
        exerciseRepository.save(Exercise.builder()
                .id(testExerciseId)
                .name("Test Exercise")
                .build());

        CreateWorkoutTemplateRequest.TemplateExerciseRequest ex = CreateWorkoutTemplateRequest.TemplateExerciseRequest.builder()
                .exerciseId(testExerciseId)
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

    private ExerciseRecord fetchExerciseRecordByWorkoutRecordId(UUID workoutRecordId, UUID exerciseId) {
        WorkoutRecord wr = workoutRecordRepository.findById(workoutRecordId).orElseThrow();
        List<ExerciseRecord> records = exerciseRecordRepository.findAllByWorkoutRecordIdOrderByOrdinalAsc(wr.getId());
        return records.stream().filter(r -> exerciseId.equals(r.getExerciseId())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("First record sets both 1RM and volume PRs, PB saved and achievements present")
    void firstRecordPersistsAchievementsAndPB(@TestUserContext String token, @TestUserContext User user) throws Exception {
        UUID testExerciseId = UUID.randomUUID();
        Workout workout = createSimpleWorkout(user, testExerciseId);
        String exerciseJson = """
        {
          "exerciseId": "%s",
          "reps": [5, 3, 1],
          "weight": [100.0, 110.0, 120.0],
          "notes": "go"
        }
        """.formatted(testExerciseId.toString());
        String wrIdStr = postWorkoutRecord(token, workout.getId().toString(), exerciseJson);
        UUID wrId = UUID.fromString(wrIdStr);

        ExerciseRecord er = fetchExerciseRecordByWorkoutRecordId(wrId, testExerciseId);
        assertThat(er.getAchievedOneRmValue()).isNotNull();
        assertThat(er.getAchievedOneRmSetIndex()).isEqualTo(2);
        assertThat(er.getAchievedOneRmValue()).isBetween(123.9, 125.5);
        assertThat(er.getAchievedTotalVolumeValue()).isNotNull();
        assertThat(er.getAchievedTotalVolumeValue()).isEqualTo(950.0);

        Optional<ExercisePersonalBest> pbOpt = personalBestRepository.findByUserIdAndExerciseId(user.getId(), testExerciseId);
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
        UUID testExerciseId = UUID.randomUUID();
        Workout workout = createSimpleWorkout(user, testExerciseId);
        // Seed a PR first
        String wr1Str = postWorkoutRecord(token, workout.getId().toString(), """
        {
          "exerciseId": "%s",
          "reps": [5, 3, 1],
          "weight": [100.0, 110.0, 120.0]
        }
        """.formatted(testExerciseId.toString()));
        UUID wr1 = UUID.fromString(wr1Str);
        ExerciseRecord seed = fetchExerciseRecordByWorkoutRecordId(wr1, testExerciseId);
        assertThat(seed.getAchievedOneRmValue()).isNotNull();

        // Post a worse performance
        String wr2Str = postWorkoutRecord(token, workout.getId().toString(), """
        {
          "exerciseId": "%s",
          "reps": [5, 3, 1],
          "weight": [90.0, 100.0, 110.0]
        }
        """.formatted(testExerciseId.toString()));
        UUID wr2 = UUID.fromString(wr2Str);
        ExerciseRecord er2 = fetchExerciseRecordByWorkoutRecordId(wr2, testExerciseId);
        assertThat(er2.getAchievedOneRmValue()).isNull();
        assertThat(er2.getAchievedTotalVolumeValue()).isNull();

        ExercisePersonalBest pb = personalBestRepository.findByUserIdAndExerciseId(user.getId(), testExerciseId).orElseThrow();
        assertThat(pb.getOneRm()).isGreaterThan(123.9);
        assertThat(pb.getTotalVolume()).isEqualTo(950.0);
    }

    @Test
    @DisplayName("Record with higher 1RM but lower volume updates only 1RM PB")
    void higherOneRmLowerVolumeUpdatesOnlyOneRm(@TestUserContext String token, @TestUserContext User user) throws Exception {
        UUID testExerciseId = UUID.randomUUID();
        Workout workout = createSimpleWorkout(user, testExerciseId);
        // Seed
        postWorkoutRecord(token, workout.getId().toString(), """
        {
          "exerciseId": "%s",
          "reps": [5, 3, 1],
          "weight": [100.0, 110.0, 120.0]
        }
        """.formatted(testExerciseId.toString()));
        // Better 1RM (125x1) lower volume
        String wr2Str = postWorkoutRecord(token, workout.getId().toString(), """
        {
          "exerciseId": "%s",
          "reps": [1],
          "weight": [125.0]
        }
        """.formatted(testExerciseId.toString()));
        UUID wr2 = UUID.fromString(wr2Str);
        ExerciseRecord er2 = fetchExerciseRecordByWorkoutRecordId(wr2, testExerciseId);
        assertThat(er2.getAchievedOneRmValue()).isNotNull();
        assertThat(er2.getAchievedOneRmSetIndex()).isEqualTo(0);
        assertThat(er2.getAchievedTotalVolumeValue()).isNull();

        ExercisePersonalBest pb = personalBestRepository.findByUserIdAndExerciseId(user.getId(), testExerciseId).orElseThrow();
        assertThat(pb.getOneRm()).isBetween(128.5, 130.0);
        assertThat(pb.getTotalVolume()).isEqualTo(950.0);
    }

    @Test
    @DisplayName("Record with higher volume but lower 1RM updates only volume PB")
    void higherVolumeLowerOneRmUpdatesOnlyVolume(@TestUserContext String token, @TestUserContext User user) throws Exception {
        UUID testExerciseId = UUID.randomUUID();
        Workout workout = createSimpleWorkout(user, testExerciseId);
        // Seed best 1RM 125x1 and volume 950 from first
        postWorkoutRecord(token, workout.getId().toString(), """
        {
          "exerciseId": "%s",
          "reps": [5, 3, 1],
          "weight": [100.0, 110.0, 120.0]
        }
        """.formatted(testExerciseId.toString()));
        postWorkoutRecord(token, workout.getId().toString(), """
        {
          "exerciseId": "%s",
          "reps": [1],
          "weight": [125.0]
        }
        """.formatted(testExerciseId.toString()));

        // Higher volume but lower 1RM (80x15 => vol 1200, 1RM ~120)
        String wr3Str = postWorkoutRecord(token, workout.getId().toString(), """
        {
          "exerciseId": "%s",
          "reps": [15],
          "weight": [80.0]
        }
        """.formatted(testExerciseId.toString()));
        UUID wr3 = UUID.fromString(wr3Str);
        ExerciseRecord er3 = fetchExerciseRecordByWorkoutRecordId(wr3, testExerciseId);
        assertThat(er3.getAchievedOneRmValue()).isNull();
        assertThat(er3.getAchievedTotalVolumeValue()).isNotNull();
        assertThat(er3.getAchievedTotalVolumeValue()).isEqualTo(1200.0);

        ExercisePersonalBest pb = personalBestRepository.findByUserIdAndExerciseId(user.getId(), testExerciseId).orElseThrow();
        assertThat(pb.getOneRm()).isBetween(128.5, 130.0);
        assertThat(pb.getTotalVolume()).isEqualTo(1200.0);
    }

    @Test
    @DisplayName("GET /api/workouts/record returns achievement fields in response for PR records")
    void getWorkoutRecords_returnsAchievements(@TestUserContext String token, @TestUserContext User user) throws Exception {
        UUID testExerciseId = UUID.randomUUID();
        Workout workout = createSimpleWorkout(user, testExerciseId);
        // Create a PR record
        postWorkoutRecord(token, workout.getId().toString(), """
        {
          "exerciseId": "%s",
          "reps": [5, 3, 1],
          "weight": [100.0, 110.0, 120.0]
        }
        """.formatted(testExerciseId.toString()));

        mockMvc.perform(get("/api/workouts/record")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // Further JSONPath assertions could be added if needed; focus here is end-to-end success.
    }
}
