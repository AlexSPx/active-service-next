package com.services.active.models.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public enum Equipment {
    MEDICINE_BALL("medicine ball"),
    DUMBBELL("dumbbell"),
    BODY_ONLY("body only"),
    BANDS("bands"),
    KETTLEBELLS("kettlebells"),
    FOAM_ROLL("foam roll"),
    CABLE("cable"),
    MACHINE("machine"),
    BARBELL("barbell"),
    EXERCISE_BALL("exercise ball"),
    E_Z_CURL_BAR("e-z curl bar"),
    OTHER("other");

    private final String name;

    Equipment(String name) {
        this.name = name;
    }

    @JsonCreator
    public static Equipment fromName(String name) {
        for (Equipment equipment : Equipment.values()) {
            if (equipment.getName().equalsIgnoreCase(name)) {
                return equipment;
            }
        }

        return null;
    }
}