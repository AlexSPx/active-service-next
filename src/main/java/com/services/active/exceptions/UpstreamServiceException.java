package com.services.active.exceptions;

import org.springframework.http.HttpStatusCode;

public class UpstreamServiceException extends ApiException {
    public UpstreamServiceException(HttpStatusCode status, String message) {
        super(status, message);
    }
}
