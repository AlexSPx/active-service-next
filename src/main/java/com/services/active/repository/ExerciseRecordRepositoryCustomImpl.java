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
    public List<String> saveAllAndReturnIds(Iterable<ExerciseRecord> exerciseRecords) {
        List<String> ids = new ArrayList<>();
        for (ExerciseRecord record : exerciseRecords) {
            ExerciseRecord saved = mongoTemplate.save(record);
            ids.add(saved.getId());
        }
        return ids;
    }
}
