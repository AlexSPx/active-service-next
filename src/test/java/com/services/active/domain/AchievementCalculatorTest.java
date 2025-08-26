package com.services.active.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AchievementCalculatorTest {

    @Test
    void computesBestEstimatedOneRmAndSetIndex() {
        var reps = List.of(5, 3, 1);
        var weights = List.of(100.0, 110.0, 120.0);
        var result = AchievementCalculator.computeBestEstimatedOneRm(reps, weights);
        assertNotNull(result);
        assertEquals(2, result.bestSetIndex());
        // expected around 120 * 1.0333 = 124.0
        assertTrue(result.bestOneRm() > 123.0 && result.bestOneRm() < 125.5);
    }

    @Test
    void computesTotalVolumeAcrossSets() {
        var reps = List.of(5, 3);
        var weights = List.of(100.0, 110.0);
        double volume = AchievementCalculator.computeTotalVolume(reps, weights);
        assertEquals(830.0, volume, 1e-6);
    }
}

