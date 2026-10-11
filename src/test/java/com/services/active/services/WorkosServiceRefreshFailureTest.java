package com.services.active.services;

import com.fasterxml.jackson.core.JsonParseException;
import com.services.active.GlobalErrorHandler;
import com.services.active.exceptions.ApiException;
import com.services.active.exceptions.UnauthorizedException;
import com.workos.common.exceptions.BadRequestException;
import com.workos.common.exceptions.GenericServerException;
import com.workos.common.exceptions.UnprocessableEntityException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class WorkosServiceRefreshFailureTest {

    @Test
    void classifyRefreshFailure_invalidGrant_returnsUnauthorized() {
        RuntimeException result = WorkosService.classifyRefreshFailure(
                new BadRequestException("raw refresh details", "invalid_grant", null, "request-id"));

        assertThat(result).isInstanceOf(UnauthorizedException.class);
        assertApiResponse(result, 401, "Invalid or expired refresh token");
    }

    @Test
    void classifyRefreshFailure_invalidRequest_returnsClientError() {
        assertApiResponse(WorkosService.classifyRefreshFailure(
                new BadRequestException("raw refresh details", "invalid_request", null, "request-id")),
                400, "Invalid token refresh request");
        assertApiResponse(WorkosService.classifyRefreshFailure(
                new BadRequestException("raw refresh details", null, null, "request-id")),
                400, "Invalid token refresh request");
        assertApiResponse(WorkosService.classifyRefreshFailure(
                new UnprocessableEntityException("raw refresh details", "invalid_request", null, "request-id")),
                422, "Invalid token refresh request");
        assertApiResponse(WorkosService.classifyRefreshFailure(
                new GenericServerException("raw refresh details", 403, "request-id")),
                403, "Token refresh request rejected");
    }

    @Test
    void classifyRefreshFailure_transientProviderError_preservesStatus() {
        for (int status : new int[]{408, 429, 500, 502, 503, 504, 599}) {
            assertApiResponse(WorkosService.classifyRefreshFailure(
                    new GenericServerException("raw refresh details", status, "request-id")),
                    status, "Token refresh temporarily unavailable");
        }
    }

    @Test
    void classifyRefreshFailure_transportError_returnsUnavailableOrTimeout() {
        assertApiResponse(WorkosService.classifyRefreshFailure(new IOException("raw refresh details")),
                503, "Token refresh temporarily unavailable");
        assertApiResponse(WorkosService.classifyRefreshFailure(
                new RuntimeException(new IOException("raw refresh details"))),
                503, "Token refresh temporarily unavailable");
        assertApiResponse(WorkosService.classifyRefreshFailure(new SocketTimeoutException("raw refresh details")),
                504, "Token refresh timed out");
        assertApiResponse(WorkosService.classifyRefreshFailure(
                new RuntimeException(new IOException(new SocketTimeoutException("raw refresh details")))),
                504, "Token refresh timed out");
    }

    @Test
    void classifyRefreshFailure_unexpectedError_returnsSafeInternalError() {
        for (Exception error : new Exception[]{
                new com.workos.common.exceptions.UnauthorizedException("raw refresh details", "request-id"),
                new com.workos.common.exceptions.NotFoundException("raw refresh details", "request-id"),
                new GenericServerException("raw refresh details", 401, "request-id"),
                new GenericServerException("raw refresh details", 200, "request-id"),
                new IllegalStateException("raw refresh details"),
                new Exception("raw refresh details"),
                new JsonParseException(null, "raw refresh details"),
                new RuntimeException(new JsonParseException(null, "raw refresh details"))}) {
            RuntimeException result = WorkosService.classifyRefreshFailure(error);

            assertThat(result).isNotInstanceOf(ApiException.class);
            var response = new GlobalErrorHandler().handleGeneric(result);
            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(response.getBody()).containsOnlyKeys("message").containsEntry("message", "Unexpected error");
        }
    }

    private static void assertApiResponse(RuntimeException result, int status, String message) {
        assertThat(result).isInstanceOf(ApiException.class);
        var response = new GlobalErrorHandler().handleApiException((ApiException) result);
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).containsOnlyKeys("message").containsEntry("message", message);
    }
}
