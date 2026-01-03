package com.services.active.models.types;

public enum StreakUpdateStatus {
    CONTINUED,
    STARTED,
    WRONG_WORKOUT,
    BROKEN_RESET,
    /**
     * For WEEKLY_COMPLETION routines: workout counted towards weekly goal but week not yet complete.
     */
    WEEKLY_PROGRESS
}

