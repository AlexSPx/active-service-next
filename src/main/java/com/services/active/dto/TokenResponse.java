package com.services.active.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "JWT token response after successful authentication")
public class TokenResponse {
    @Schema(description = "JWT bearer token for API authentication",
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c")
    private String token;

    @Schema(description = "Refresh token to obtain a new access token when the current one expires",
            example = "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4...")
    private String refreshToken;
}
