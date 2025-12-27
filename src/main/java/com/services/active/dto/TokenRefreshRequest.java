package com.services.active.dto;

import lombok.Data;

@Data
public class TokenRefreshRequest {
    private String refreshToken;
}
