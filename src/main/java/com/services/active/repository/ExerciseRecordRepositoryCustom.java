package com.services.active.repository;

import com.services.active.models.ExerciseRecord;

import java.util.List;

public interface ExerciseRecordRepositoryCustom {
    List<ExerciseRecord> saveAllAndReturn(Iterable<ExerciseRecord> exerciseRecords);
}
