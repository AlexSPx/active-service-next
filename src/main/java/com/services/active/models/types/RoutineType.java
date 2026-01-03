package com.services.active.models.types;

public enum RoutineType {
    /**
     * Sequential routine where workouts are assigned to specific days in a repeating cycle.
     * dayIndex in RoutinePattern represents the specific day (0 = day 1, 1 = day 2, etc.)
     */
    SEQUENTIAL,
    
    /**
     * Weekly completion routine where all workouts must be completed within a week (Monday-Sunday).
     * dayIndex in RoutinePattern represents the workout order/identifier (A=0, B=1, C=2).
     * Workouts can be completed in any order, the week resets on Monday.
     */
    WEEKLY_COMPLETION
}

