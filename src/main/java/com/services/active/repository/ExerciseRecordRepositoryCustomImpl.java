package com.services.active.repository;

import com.services.active.models.ExerciseRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public class ExerciseRecordRepositoryCustomImpl implements ExerciseRecordRepositoryCustom {
    private final ReactiveMongoTemplate mongoTemplate;

    @Autowired
    public ExerciseRecordRepositoryCustomImpl(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Flux<String> saveAllAndReturnIds(Iterable<ExerciseRecord> exerciseRecords) {
        return Flux.fromIterable(exerciseRecords)
                .flatMap(record -> mongoTemplate.save(record)
                        .map(ExerciseRecord::getId));
    }
}

