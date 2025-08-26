package com.services.active.domain;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class AchievementCalculator {
    public static BestOneRmResult computeBestEstimatedOneRm(List<Integer> reps, List<Double> weight) {
        if (reps == null || weight == null) return new BestOneRmResult(null, null);
        int n = Math.min(reps.size(), weight.size());
        Double best = null;
        Integer bestIdx = null;
        for (int i = 0; i < n; i++) {
            Integer r = reps.get(i);
            Double w = weight.get(i);
            if (r == null || w == null || r <= 0 || w <= 0) continue;
            // Epley formula: 1RM = w * (1 + r/30)
            double est = w * (1.0 + (r / 30.0));
            if (best == null || est > best) {
                best = est;
                bestIdx = i;
            }
        }
        return new BestOneRmResult(best, bestIdx);
    }

    public static Double computeTotalVolume(List<Integer> reps, List<Double> weight) {
        if (reps == null || weight == null) return null;
        int n = Math.min(reps.size(), weight.size());
        double sum = 0.0;
        boolean any = false;
        for (int i = 0; i < n; i++) {
            Integer r = reps.get(i);
            Double w = weight.get(i);
            if (r == null || w == null || r <= 0 || w <= 0) continue;
            sum += r * w;
            any = true;
        }
        return any ? sum : null;
    }

    public record BestOneRmResult(Double bestOneRm, Integer bestSetIndex) {}
}

