package com.services.active.models.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public enum MechanicType {
    ISOLATION("isolation"), COMPOUND("compound");

    private final String name;

    MechanicType(String name) {
        this.name = name;
    }

    @JsonCreator
    public static MechanicType fromName(String name) {
        for (MechanicType type : MechanicType.values()) {
            if (type.getName() != null && type.getName().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null; // Return NONE if no match is found
    }
}