package com.services.active.dataloader;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.services.active.models.Exercise;
import com.services.active.repository.ExerciseRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class ExerciseLoaderConfig {
    private final ExerciseRepository exerciseRepository;
    private final ObjectMapper mapper = new ObjectMapper().configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
            long count = exerciseRepository.count();
            if (count == 0) {
                log.info("Exercises collection empty — loading remote JSON.");
                String json = RestClient.create()
                        .get()
                        .uri("https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json")
                        .retrieve()
                        .body(String.class);

                List<Exercise> list = mapper.readValue(json, new TypeReference<List<Exercise>>() {});
                exerciseRepository.saveAll(list);
                log.info("Imported {} exercises", list.size());
            } else {
                log.info("Exercises already loaded (count: {})", count);
            }
        };
    }
}
