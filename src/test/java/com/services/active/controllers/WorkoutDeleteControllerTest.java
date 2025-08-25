package com.services.active.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.services.active.config.IntegrationTestBase;
import com.services.active.config.user.TestUserContext;
import com.services.active.config.user.WithTestUser;
import com.services.active.dto.CreateWorkoutRequest;
import com.services.active.dto.CreateWorkoutTemplateRequest;
import com.services.active.dto.WorkoutRecordRequest;
import com.services.active.models.TemplateExercise;
import com.services.active.models.User;
import com.services.active.models.Workout;
import com.services.active.models.WorkoutTemplate;
import com.services.active.models.WorkoutRecord;
import com.services.active.repository.WorkoutRecordRepository;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithTestUser
@SpringBootTest
@AutoConfigureMockMvc
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class WorkoutDeleteControllerTest extends IntegrationTestBase {

    private final MockMvc mockMvc;
    private final WorkoutService workoutService;
    private final WorkoutRepository workoutRepository;
    private final WorkoutTemplateRepository workoutTemplateRepository;
    private final WorkoutRecordRepository workoutRecordRepository;

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
                .title("Delete Me Workout")
                .notes("To be deleted in test")
                .template(template)
                .build();

        return workoutService.createWorkout(user.getId(), request);
    }

    @Test
    @DisplayName("DELETE /api/workouts/{id} -> 204 NO CONTENT deletes workout and template but keeps records")
    void deleteWorkout_deletesWorkoutAndTemplate_keepsRecords(@TestUserContext String token, @TestUserContext User user) throws Exception {
        // Create workout
        Workout workout = createWorkout(user);
        String templateId = workout.getTemplateId();

        // Create a record for this workout
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(30);
        WorkoutRecordRequest.ExerciseRecord ex = new WorkoutRecordRequest.ExerciseRecord(
                "exercise-1",
                List.of(10, 8, 6),
                List.of(50.0, 55.0, 60.0),
                null,
                null
        );
        WorkoutRecordRequest recordReq = new WorkoutRecordRequest();
        recordReq.setWorkoutId(workout.getId());
        recordReq.setNotes("Record to survive delete");
        recordReq.setStartTime(startTime);
        recordReq.setExerciseRecords(List.of(ex));
        String recordId = workoutService.createWorkoutRecord(user.getId(), recordReq);

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
    }
}
