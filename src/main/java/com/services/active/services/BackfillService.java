package com.services.active.services;

import com.services.active.domain.AchievementCalculator;
import com.services.active.models.ExercisePersonalBest;
import com.services.active.models.ExerciseRecord;
import com.services.active.repository.ExercisePersonalBestRepository;
import com.services.active.repository.ExerciseRecordRepository;
import com.services.active.repository.UserRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackfillService {

    private final ExerciseRecordRepository exerciseRecordRepository;
    private final ExercisePersonalBestRepository personalBestRepository;
    private final UserRepository userRepository;

    @Data
    @Builder
    public static class BackfillResult {
        private String scope; // all|user|user-exercise
        private UUID userId;
        private UUID exerciseId;
        private int recordsEvaluated;
        private int recordsUpdated;
        private int personalBestsUpserted;
    }

    public BackfillResult backfillAllUsers() {
        int totalEvaluated = 0, totalUpdated = 0, totalPb = 0;
        var users = userRepository.findAll();
        for (var user : users) {
            var res = backfillUser(user.getId());
            totalEvaluated += res.getRecordsEvaluated();
            totalUpdated += res.getRecordsUpdated();
            totalPb += res.getPersonalBestsUpserted();
        }
        return BackfillResult.builder()
                .scope("all")
                .recordsEvaluated(totalEvaluated)
                .recordsUpdated(totalUpdated)
                .personalBestsUpserted(totalPb)
                .build();
    }

    public BackfillResult backfillUser(UUID userId) {
        List<ExerciseRecord> all = exerciseRecordRepository.findByUserIdOrderByCreatedAtAsc(userId);
        Map<UUID, List<ExerciseRecord>> byExercise = all.stream().collect(Collectors.groupingBy(ExerciseRecord::getExerciseId, LinkedHashMap::new, Collectors.toList()));
        int evaluated = 0, updated = 0, pbUpserts = 0;
        for (Map.Entry<UUID, List<ExerciseRecord>> entry : byExercise.entrySet()) {
            BackfillResult res = backfillUserExerciseInternal(userId, entry.getKey(), entry.getValue());
            evaluated += res.getRecordsEvaluated();
            updated += res.getRecordsUpdated();
            pbUpserts += res.getPersonalBestsUpserted();
        }
        return BackfillResult.builder()
                .scope("user")
                .userId(userId)
                .recordsEvaluated(evaluated)
                .recordsUpdated(updated)
                .personalBestsUpserted(pbUpserts)
                .build();
    }

    public BackfillResult backfillUserExercise(UUID userId, UUID exerciseId) {
        List<ExerciseRecord> list = exerciseRecordRepository.findByUserIdAndExerciseIdOrderByCreatedAtAsc(userId, exerciseId);
        return backfillUserExerciseInternal(userId, exerciseId, list);
    }

    private BackfillResult backfillUserExerciseInternal(UUID userId, UUID exerciseId, List<ExerciseRecord> records) {
        double bestOneRm = Double.NEGATIVE_INFINITY;
        int bestOneRmSetIndex = -1;
        UUID bestOneRmRecordId = null;
        double bestVolume = Double.NEGATIVE_INFINITY;
        UUID bestVolumeRecordId = null;

        int evaluated = 0, updated = 0, pbUpserts = 0;
        for (ExerciseRecord r : records) {
            evaluated++;
            boolean changed = false;

            var reps = r.getReps();
            var weight = r.getWeight();
            boolean hasStrength = reps != null && weight != null && !reps.isEmpty() && !weight.isEmpty();

            // Default clear
            if (r.getAchievedOneRmValue() != null) {
                r.setAchievedOneRmValue(null);
                r.setAchievedOneRmSetIndex(null);
                changed = true;
            }
            if (r.getAchievedTotalVolumeValue() != null) {
                r.setAchievedTotalVolumeValue(null);
                changed = true;
            }

            if (hasStrength) {
                var best = AchievementCalculator.computeBestEstimatedOneRm(reps, weight);
                Double est1Rm = best.bestOneRm();
                Integer setIdx = best.bestSetIndex();
                Double vol = AchievementCalculator.computeTotalVolume(reps, weight);

                if (est1Rm != null && est1Rm > bestOneRm) {
                    r.setAchievedOneRmValue(est1Rm);
                    r.setAchievedOneRmSetIndex(setIdx);
                    changed = true;
                    bestOneRm = est1Rm;
                    bestOneRmSetIndex = setIdx != null ? setIdx : -1;
                    bestOneRmRecordId = r.getId();
                }
                if (vol != null && vol > bestVolume) {
                    r.setAchievedTotalVolumeValue(vol);
                    changed = true;
                    bestVolume = vol;
                    bestVolumeRecordId = r.getId();
                }
            }

            if (changed) {
                exerciseRecordRepository.save(r);
                updated++;
            }
        }

        // Upsert PB once per exercise from computed bests
        if (!records.isEmpty()) {
            ExercisePersonalBest pb = personalBestRepository.findByUserIdAndExerciseId(userId, exerciseId)
                    .orElse(ExercisePersonalBest.builder().userId(userId).exerciseId(exerciseId).build());
            boolean pbChanged = false;
            if (bestOneRm > Double.NEGATIVE_INFINITY && (pb.getOneRm() == null || bestOneRm > pb.getOneRm())) {
                pb.setOneRm(bestOneRm);
                pb.setOneRmRecordId(bestOneRmRecordId);
                pb.setOneRmRecordSetIndex(bestOneRmSetIndex >= 0 ? bestOneRmSetIndex : null);
                pbChanged = true;
            }
            if (bestVolume > Double.NEGATIVE_INFINITY && (pb.getTotalVolume() == null || bestVolume > pb.getTotalVolume())) {
                pb.setTotalVolume(bestVolume);
                pb.setTotalVolumeRecordId(bestVolumeRecordId);
                pbChanged = true;
            }
            if (pb.getCreatedAt() == null) pb.setCreatedAt(java.time.LocalDateTime.now());
            pb.setUpdatedAt(java.time.LocalDateTime.now());
            if (pbChanged) {
                personalBestRepository.save(pb);
                pbUpserts++;
            } else if (pb.getId() == null) {
                personalBestRepository.save(pb);
                pbUpserts++;
            }
        }

        return BackfillResult.builder()
                .scope("user-exercise")
                .userId(userId)
                .exerciseId(exerciseId)
                .recordsEvaluated(evaluated)
                .recordsUpdated(updated)
                .personalBestsUpserted(pbUpserts)
                .build();
    }
}
