package com.services.active.repository;

import com.services.active.models.ExerciseRecord;
import reactor.core.publisher.Flux;

public interface ExerciseRecordRepositoryCustom {
    Flux<String> saveAllAndReturnIds(Iterable<ExerciseRecord> exerciseRecords);
}

