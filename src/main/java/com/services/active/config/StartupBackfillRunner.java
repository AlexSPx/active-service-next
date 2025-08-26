package com.services.active.config;

import com.services.active.services.BackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class StartupBackfillRunner implements ApplicationRunner {

    private final BackfillService backfillService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Starting achievements backfill on service startup...");
            var result = backfillService.backfillAllUsers();
            log.info("Achievements backfill finished: evaluated={}, updated={}, pbUpserts={}",
                    result.getRecordsEvaluated(), result.getRecordsUpdated(), result.getPersonalBestsUpserted());
        } catch (Exception e) {
            log.error("Achievements backfill failed on startup", e);
        }
    }
}

