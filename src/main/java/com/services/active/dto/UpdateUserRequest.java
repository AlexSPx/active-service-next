package com.services.active.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Partial update request for current user. Missing fields are not modified.")
public class UpdateUserRequest {
    @Schema(description = "New username; if omitted, username unchanged", example = "new_handle")
    private String username;
    @Schema(description = "New first name; if omitted, first name unchanged", example = "Alice")
    private String firstName;
    @Schema(description = "New last name; if omitted, last name unchanged", example = "Smith")
    private String lastName;
    @Schema(description = "New email; must be unique; if omitted, email unchanged", example = "alice.smith@example.com")
    private String email;
    @Schema(description = "New timezone; if omitted, timezone unchanged", example = "Europe/Sofia")
    private String timezone;
    @Schema(description = "Amount of notifications per week; if omitted, preferences unchanged", example = "3")
    private Integer notificationFrequency;

    @Schema(description = "Updated body measurements object (optional; only provided non-null fields are updated)")
    private BodyMeasurementsRequest measurements;

    @Schema(description = "Flag indicating if registration is complete; if omitted, status unchanged", example = "true")
    private Boolean registrationCompleted;
}
