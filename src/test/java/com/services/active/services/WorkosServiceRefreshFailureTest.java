package com.services.active.services;

import com.services.active.exceptions.UnauthorizedException;
import com.workos.common.exceptions.BadRequestException;
import com.workos.common.exceptions.GenericServerException;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class WorkosServiceRefreshFailureTest {

    @Test
    void classifiesOnlyInvalidGrantAsUnauthorizedAndKeepsTransientFailuresRetryable() {
        RuntimeException invalidGrant = WorkosService.classifyRefreshFailure(
                new BadRequestException("raw refresh details", "invalid_grant", null, "request-id"));
        assertInstanceOf(UnauthorizedException.class, invalidGrant);
        assertEquals("Invalid or expired refresh token", invalidGrant.getMessage());

        assertTransientStatus(408, 408);
        assertTransientStatus(429, 429);
        assertTransientStatus(502, 502);
        assertUnavailable(WorkosService.classifyRefreshFailure(
                new BadRequestException("raw refresh details", "invalid_request", null, "request-id")));
        assertUnavailable(WorkosService.classifyRefreshFailure(
                new com.workos.common.exceptions.UnauthorizedException("raw refresh details", "request-id")));

        ResponseStatusException transport = assertInstanceOf(ResponseStatusException.class,
                WorkosService.classifyRefreshFailure(new IOException("raw refresh details")));
        assertEquals(503, transport.getStatusCode().value());
        assertEquals("Token refresh temporarily unavailable", transport.getReason());

        ResponseStatusException timeout = assertInstanceOf(ResponseStatusException.class,
                WorkosService.classifyRefreshFailure(new SocketTimeoutException("raw refresh details")));
        assertEquals(504, timeout.getStatusCode().value());
        assertEquals("Token refresh timed out", timeout.getReason());
    }

    private static void assertUnavailable(RuntimeException result) {
        ResponseStatusException unavailable = assertInstanceOf(ResponseStatusException.class, result);
        assertEquals(503, unavailable.getStatusCode().value());
    }

    private static void assertTransientStatus(int upstreamStatus, int expectedStatus) {
        ResponseStatusException result = assertInstanceOf(ResponseStatusException.class,
                WorkosService.classifyRefreshFailure(
                        new GenericServerException("raw refresh details", upstreamStatus, "request-id")));
        assertEquals(expectedStatus, result.getStatusCode().value());
        assertEquals("Token refresh temporarily unavailable", result.getReason());
    }
}
