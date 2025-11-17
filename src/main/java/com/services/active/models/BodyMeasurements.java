package com.services.active.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User body measurements stored in metric units only.")
public class BodyMeasurements {
    @Schema(description = "Body weight in kilograms (optional; must be > 0 if provided)", example = "72.5")
    private Double weightKg;
    @Schema(description = "Body height in centimeters (optional; must be > 0 if provided)", example = "180")
    private Integer heightCm;
}

