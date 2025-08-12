package com.services.active.models.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public enum MuscleGroup {
    ABDOMINALS("abdominals"),
    ABDUCTORS("abductors"),
    ADDUCTORS("adductors"),
    BICEPS("biceps"),
    CALVES("calves"),
    CHEST("chest"),
    FOREARMS("forearms"),
    GLUTES("glutes"),
    HAMSTRINGS("hamstrings"),
    LATS("lats"),
    LOWER_BACK("lower back"),
    MIDDLE_BACK("middle back"),
    NECK("neck"),
    QUADRICEPS("quadriceps"),
    SHOULDERS("shoulders"),
    TRAPS("traps"),
    TRICEPS("triceps");

    private final String name;

    MuscleGroup(String name) {
        this.name = name;
    }

    @JsonCreator
    public static MuscleGroup fromName(String name) {
        for (MuscleGroup muscleGroup : MuscleGroup.values()) {
            if (muscleGroup.getName().equalsIgnoreCase(name)) {
                return muscleGroup;
            }
        }
        return null; // Return null if no match is found
    }
}