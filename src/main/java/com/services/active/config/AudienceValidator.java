package com.services.active.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {
    private final String requiredAudience;
    private static final OAuth2Error ERROR = new OAuth2Error("invalid_token", "The required audience is missing or invalid", null);

    public AudienceValidator(String requiredAudience) {
        this.requiredAudience = requiredAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audiences = token.getAudience();
        if (requiredAudience == null || requiredAudience.isBlank()) {
            return OAuth2TokenValidatorResult.success();
        }
        if (audiences != null && audiences.stream().anyMatch(requiredAudience::equals)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(ERROR);
    }
}

