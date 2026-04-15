package com.services.active.services;

import com.services.active.models.ExercisePersonalBest;
import com.services.active.models.ExerciseRecord;
import com.services.active.repository.ExercisePersonalBestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PersonalBestService {

    private final ExercisePersonalBestRepository personalBestRepository;

    public Map<UUID, ExercisePersonalBest> getCurrentPbs(UUID userId, Set<UUID> exerciseIds) {
        Map<UUID, ExercisePersonalBest> map = new HashMap<>();
        for (UUID exId : exerciseIds) {
            personalBestRepository.findByUserIdAndExerciseId(userId, exId)
                    .ifPresent(pb -> map.put(exId, pb));
        }
        return map;
    }

    public void persistPrs(UUID userId, List<ExerciseRecord> savedExerciseRecords) {
        for (ExerciseRecord record : savedExerciseRecords) {
            boolean prOneRm = record.getAchievedOneRmValue() != null;
            boolean prVolume = record.getAchievedTotalVolumeValue() != null;
            if (!prOneRm && !prVolume) continue;

            UUID exerciseId = record.getExerciseId();
            ExercisePersonalBest pb = personalBestRepository
                    .findByUserIdAndExerciseId(userId, exerciseId)
                    .orElse(ExercisePersonalBest.builder()
                            .userId(userId)
                            .exerciseId(exerciseId)
                            .createdAt(LocalDateTime.now())
                            .build());

            if (prOneRm) {
                pb.setOneRm(record.getAchievedOneRmValue());
                pb.setOneRmRecordId(record.getId());
                pb.setOneRmRecordSetIndex(record.getAchievedOneRmSetIndex());
            }
            if (prVolume) {
                pb.setTotalVolume(record.getAchievedTotalVolumeValue());
                pb.setTotalVolumeRecordId(record.getId());
            }
            pb.setUpdatedAt(LocalDateTime.now());
            personalBestRepository.save(pb);
        }
    }
}
