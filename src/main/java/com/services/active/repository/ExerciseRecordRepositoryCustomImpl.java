package com.services.active.repository;

import com.services.active.models.ExerciseRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class ExerciseRecordRepositoryCustomImpl implements ExerciseRecordRepositoryCustom {
    private final MongoTemplate mongoTemplate;

    @Autowired
    public ExerciseRecordRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<ExerciseRecord> saveAllAndReturn(Iterable<ExerciseRecord> exerciseRecords) {
        List<ExerciseRecord> savedList = new ArrayList<>();
        for (ExerciseRecord record : exerciseRecords) {
            ExerciseRecord saved = mongoTemplate.save(record);
            savedList.add(saved);
        }
        return savedList;
    }
}
