package com.services.active.dataloader;

import com.services.active.services.ExerciseCatalogImporter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ExerciseLoaderConfig {
    private final ExerciseCatalogImporter importer;
    private static final Logger log = LoggerFactory.getLogger(ExerciseLoaderConfig.class);

    @Value("${active.exercises.autoload:true}")
    private boolean autoLoad;

    @Bean
    ApplicationRunner loadExercisesRunner() {
        return args -> {
            if (!autoLoad) {
                log.info("Exercise autoload disabled.");
                return;
            }
            var report = importer.importPinnedCatalog();
            log.info("Exercise catalog import: {}", report);
        };
    }
}
