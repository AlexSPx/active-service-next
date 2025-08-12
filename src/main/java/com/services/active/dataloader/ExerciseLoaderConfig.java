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
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class ExerciseLoaderConfig {
    private final ExerciseRepository exerciseRepository;
    private final ObjectMapper mapper = new ObjectMapper().configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Logger log = LoggerFactory.getLogger(ExerciseLoaderConfig.class);
    @Bean
    ApplicationRunner loadExercisesRunner() {
        return args -> {
            exerciseRepository.count()
                    .flatMapMany(count -> {
                        if (count == 0) {
                            log.info("Exercises collection empty — loading remote JSON.");
                            return WebClient.create()
                                    .get()
                                    .uri("https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json")
                                    .retrieve()
                                    .bodyToFlux(String.class)
                                    .collectList()
                                    .flatMapMany(jsonList -> {
                                        String json = String.join("", jsonList);
                                        List<Exercise> list;
                                        try {
                                            list = mapper.readValue(json, new TypeReference<List<Exercise>>() {});
                                        } catch (Exception e) {
                                            return Flux.error(e);
                                        }
                                        return exerciseRepository.saveAll(list);
                                    });
                        } else {
                            log.info("Exercises already loaded (count: {})", count);
                            return Flux.empty();
                        }
                    })
                    .doOnNext(e -> log.info("Imported exercise: {}", e.getName()))
                    .blockLast();
        };
    }
}
