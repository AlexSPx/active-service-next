package com.services.active.models.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public enum ForceType {
    STATIC("static"),
    PULL("pull"),
    PUSH("push");

    private final String name;

    ForceType(String name) {
        this.name = name;
    }

    @JsonCreator
    public static ForceType fromName(String name) {
        for (ForceType type : ForceType.values()) {
            if (type.getName().equalsIgnoreCase(name)) {
                return type;
            }
        }

        return null;
    }
}

