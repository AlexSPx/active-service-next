package com.services.active.models.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public enum Category {
    POWERLIFTING("powerlifting"),
    STRENGTH("strength"),
    STRETCHING("stretching"),
    CARDIO("cardio"),
    OLYMPIC_WEIGHTLIFTING("olympic weightlifting"),
    STRONGMAN("strongman"),
    PLYOMETRICS("plyometrics");

    private final String name;

    Category(String name) {
        this.name = name;
    }

    @JsonCreator
    public static Category fromName(String name) {
        for (Category category : Category.values()) {
            if (category.getName().equalsIgnoreCase(name)) {
                return category;
            }
        }

        return null;
    }
}