package com.services.active.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Helper class for managing authentication cookies for web platform.
 * Creates secure HttpOnly cookies for storing JWT tokens.
 */
@Component
public class CookieHelper {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final String COOKIE_PATH = "/";
    
    // Access token expires in 5 minutes (WorkOS default)
    private static final Duration ACCESS_TOKEN_MAX_AGE = Duration.ofMinutes(5);
    // Refresh token expires in 30 days (WorkOS default)
    private static final Duration REFRESH_TOKEN_MAX_AGE = Duration.ofDays(30);

    @Value("${cookie.secure:true}")
    private boolean secure;

    @Value("${cookie.same-site:Lax}")
    private String sameSite;

    /**
     * Sets access and refresh token cookies on the response.
     */
    public void setTokenCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        ResponseCookie accessCookie = buildCookie(ACCESS_TOKEN_COOKIE, accessToken, ACCESS_TOKEN_MAX_AGE);
        ResponseCookie refreshCookie = buildCookie(REFRESH_TOKEN_COOKIE, refreshToken, REFRESH_TOKEN_MAX_AGE);

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    /**
     * Clears authentication cookies (for logout).
     */
    public void clearTokenCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = buildCookie(ACCESS_TOKEN_COOKIE, "", Duration.ZERO);
        ResponseCookie refreshCookie = buildCookie(REFRESH_TOKEN_COOKIE, "", Duration.ZERO);

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private ResponseCookie buildCookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}

