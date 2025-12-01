package com.services.active.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request object for refreshing an access token")
public class RefreshTokenRequest {
    @Schema(description = "The refresh token obtained during login/signup", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}
