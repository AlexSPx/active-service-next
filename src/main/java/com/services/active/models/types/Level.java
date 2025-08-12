package com.services.active.models.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public enum Level {
    BEGINNER("beginner"),
    INTERMEDIATE("intermediate"),
    EXPERT("expert");

    private final String name;

    Level(String name) {
        this.name = name;
    }

    @JsonCreator
    public static Level fromName(String name) {
        for (Level level : Level.values()) {
            if (level.getName().equalsIgnoreCase(name)) {
                return level;
            }
        }
        return null;
    }
}