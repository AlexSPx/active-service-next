package com.services.active.services;

import com.services.active.models.Exercise;
import com.services.active.models.ExerciseRecord;
import com.services.active.models.WorkoutRecord;
import com.services.active.models.user.User;
import com.services.active.repository.ExerciseRecordRepository;
import com.services.active.repository.ExerciseRepository;
import com.services.active.repository.UserRepository;
import com.services.active.repository.WorkoutRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkoutRecordServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private WorkoutRecordRepository workoutRecordRepository;
    @Mock private ExerciseRecordRepository exerciseRecordRepository;
    @Mock private ExerciseRepository exerciseRepository;
    @InjectMocks private WorkoutRecordService service;

    @Test
    void getWorkoutRecords_whenWorkoutWasDeleted_keepsHistoryWithNullWorkoutId() {
        UUID userId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID exerciseId = UUID.randomUUID();
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(30);
        var user = User.builder().id(userId).build();
        var record = WorkoutRecord.builder()
                .id(recordId).userId(userId).workoutId(null)
                .workoutTitle("Old workout").startTime(startTime).build();
        var exerciseRecord = ExerciseRecord.builder()
                .exerciseId(exerciseId).workoutRecordId(recordId)
                .reps(List.of(8)).weight(List.of(50.0)).build();
        when(userRepository.findByWorkosId("owner")).thenReturn(Optional.of(user));
        when(workoutRecordRepository.findAllByUserId(userId)).thenReturn(List.of(record));
        when(exerciseRecordRepository.findAllByWorkoutRecordIdOrderByOrdinalAsc(recordId))
                .thenReturn(List.of(exerciseRecord));
        when(exerciseRepository.findAllById(Set.of(exerciseId)))
                .thenReturn(List.of(Exercise.builder().id(exerciseId).name("Squat").build()));

        var history = service.getWorkoutRecords("owner");

        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getWorkoutId()).isNull();
        assertThat(history.getFirst().getWorkoutTitle()).isEqualTo("Old workout");
        assertThat(history.getFirst().getStartTime()).isEqualTo(startTime);
        assertThat(history.getFirst().getExerciseRecords()).hasSize(1);
        assertThat(history.getFirst().getExerciseRecords().getFirst().getExerciseName()).isEqualTo("Squat");
        assertThat(history.getFirst().getExerciseRecords().getFirst().getReps()).containsExactly(8);
        assertThat(history.getFirst().getExerciseRecords().getFirst().getWeight()).containsExactly(50.0);
    }
}
