package com.services.active.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/**
 * Filter that extracts JWT tokens from cookies for web platform requests.
 * When X-Platform header is "web", reads access_token from cookies and
 * converts it to an Authorization header for downstream processing.
 */
@Component
public class CookieTokenFilter extends OncePerRequestFilter {

    private static final String PLATFORM_HEADER = "X-Platform";
    private static final String WEB_PLATFORM = "web";
    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        
        String platform = request.getHeader(PLATFORM_HEADER);
        
        if (WEB_PLATFORM.equalsIgnoreCase(platform) && request.getHeader(AUTHORIZATION_HEADER) == null) {
            String accessToken = extractTokenFromCookie(request, ACCESS_TOKEN_COOKIE);
            
            if (accessToken != null) {
                HttpServletRequest wrappedRequest = new CookieAuthorizationRequestWrapper(request, accessToken);
                filterChain.doFilter(wrappedRequest, response);
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }

    private String extractTokenFromCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Request wrapper that adds Authorization header from cookie token.
     */
    private static class CookieAuthorizationRequestWrapper extends HttpServletRequestWrapper {
        private final String accessToken;

        public CookieAuthorizationRequestWrapper(HttpServletRequest request, String accessToken) {
            super(request);
            this.accessToken = accessToken;
        }

        @Override
        public String getHeader(String name) {
            if (AUTHORIZATION_HEADER.equalsIgnoreCase(name)) {
                return BEARER_PREFIX + accessToken;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (AUTHORIZATION_HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(Collections.singletonList(BEARER_PREFIX + accessToken));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
            if (!names.contains(AUTHORIZATION_HEADER)) {
                names.add(AUTHORIZATION_HEADER);
            }
            return Collections.enumeration(names);
        }
    }
}

