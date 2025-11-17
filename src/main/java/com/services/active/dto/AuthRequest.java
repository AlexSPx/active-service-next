package com.services.active.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "User registration request")
public class AuthRequest {
    @Schema(description = "User's username", example = "john_doe", required = true)
    private String username;

    @Schema(description = "User's email address", example = "john.doe@example.com", required = true)
    private String email;

    @Schema(description = "User's first name", example = "John", required = true)
    private String firstName;

    @Schema(description = "User's last name", example = "Doe", required = true)
    private String lastName;

    @Schema(description = "User's password", example = "SecurePassword123!", required = true)
    private String password;

    @Schema(description = "IANA timezone identifier", example = "America/New_York")
    private String timezone;

    @Schema(description = "Initial body measurements (optional)")
    private BodyMeasurementsRequest measurements;
}
