package com.services.active.dto;

public record GoogleUserInfo(
        String googleId,
        String email,
        String name,
        String picture,
        String givenName,
        String familyName
) {}
