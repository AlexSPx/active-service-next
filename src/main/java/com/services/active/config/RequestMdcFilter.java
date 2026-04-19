package com.services.active.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestMdcFilter extends OncePerRequestFilter {

    static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String PLATFORM_HEADER = "X-Platform";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);

        MDC.put("requestId", requestId);
        MDC.put("httpMethod", request.getMethod());
        MDC.put("httpPath", request.getRequestURI());

        String clientPlatform = request.getHeader(PLATFORM_HEADER);
        if (clientPlatform != null && !clientPlatform.isBlank()) {
            MDC.put("clientPlatform", clientPlatform);
        }

        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
            MDC.remove("httpMethod");
            MDC.remove("httpPath");
            MDC.remove("clientPlatform");
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        if (incoming != null && !incoming.isBlank()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}