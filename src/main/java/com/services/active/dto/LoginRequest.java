package com.services.active.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "User login request")
public class LoginRequest {
    @Schema(description = "User's email address", example = "john.doe@example.com", required = true)
    private String email;

    @Schema(description = "User's password", example = "SecurePassword123!", required = true)
    private String password;
}
