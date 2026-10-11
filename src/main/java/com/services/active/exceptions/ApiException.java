package com.services.active.exceptions;

import org.springframework.http.HttpStatusCode;

public abstract class ApiException extends RuntimeException {
    private final HttpStatusCode status;

    protected ApiException(HttpStatusCode status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatusCode getStatus() {
        return status;
    }
}
