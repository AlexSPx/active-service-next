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

    public Map<String, ExercisePersonalBest> getCurrentPbs(String userId, Set<String> exerciseIds) {
        Map<String, ExercisePersonalBest> map = new HashMap<>();
        for (String exId : exerciseIds) {
            personalBestRepository.findByUserIdAndExerciseId(userId, exId)
                    .ifPresent(pb -> map.put(exId, pb));
        }
        return map;
    }

    public void persistPrs(String userId, List<ExerciseRecord> exerciseRecords, List<String> exerciseRecordIds) {
        for (int i = 0; i < exerciseRecords.size(); i++) {
            ExerciseRecord record = exerciseRecords.get(i);
            String recordId = exerciseRecordIds.get(i);
            boolean prOneRm = record.getAchievedOneRm() != null;
            boolean prVolume = record.getAchievedTotalVolume() != null;
            if (!prOneRm && !prVolume) continue;

            String exerciseId = record.getExerciseId();
            ExercisePersonalBest pb = personalBestRepository
                    .findByUserIdAndExerciseId(userId, exerciseId)
                    .orElse(ExercisePersonalBest.builder()
                            .userId(userId)
                            .exerciseId(exerciseId)
                            .createdAt(LocalDateTime.now())
                            .build());

            if (prOneRm) {
                pb.setOneRm(record.getAchievedOneRm().getValue());
                pb.setOneRmRecordId(recordId);
                pb.setOneRmRecordSetIndex(record.getAchievedOneRm().getSetIndex());
            }
            if (prVolume) {
                pb.setTotalVolume(record.getAchievedTotalVolume().getValue());
                pb.setTotalVolumeRecordId(recordId);
            }
            pb.setUpdatedAt(LocalDateTime.now());
            personalBestRepository.save(pb);
        }
    }
}

