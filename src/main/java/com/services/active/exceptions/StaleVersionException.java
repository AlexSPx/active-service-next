package com.services.active.exceptions;

import org.springframework.http.HttpStatus;

public class StaleVersionException extends ApiException {
    private final Long currentVersion;

    public StaleVersionException(String message, Long currentVersion) {
        super(HttpStatus.CONFLICT, message);
        this.currentVersion = currentVersion;
    }

    public Long getCurrentVersion() {
        return currentVersion;
    }
}
