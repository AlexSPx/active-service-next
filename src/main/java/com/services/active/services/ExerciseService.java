package com.services.active.services;

import com.services.active.models.Exercise;
import com.services.active.models.types.Category;
import com.services.active.models.types.Equipment;
import com.services.active.models.types.Level;
import com.services.active.models.types.MuscleGroup;
import com.services.active.repository.ExerciseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExerciseService {
    private final ExerciseRepository exerciseRepository;
    private final MongoTemplate mongoTemplate;


    public List<Exercise> searchExercises(String name, Category category, Level level,
                                          List<MuscleGroup> primaryMuscles, List<MuscleGroup> secondaryMuscles,
                                          Equipment equipment) {
        List<Criteria> criteriaList = new ArrayList<>();

        if (name != null && !name.trim().isEmpty()) {
            criteriaList.add(Criteria.where("name").regex(name, "i"));
        }

        if (category != null) {
            criteriaList.add(Criteria.where("category").is(category));
        }

        if (level != null) {
            criteriaList.add(Criteria.where("level").is(level));
        }

        if (primaryMuscles != null && !primaryMuscles.isEmpty()) {
            criteriaList.add(Criteria.where("primaryMuscles").all(primaryMuscles));
        }

        if (secondaryMuscles != null && !secondaryMuscles.isEmpty()) {
            criteriaList.add(Criteria.where("secondaryMuscles").all(secondaryMuscles));
        }

        if (equipment != null) {
            criteriaList.add(Criteria.where("equipment").is(equipment));
        }

        if (criteriaList.isEmpty()) {
            return exerciseRepository.findAll();
        }

        Criteria combinedCriteria = new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
        Query query = new Query(combinedCriteria);

        return mongoTemplate.find(query, Exercise.class);
    }
}
