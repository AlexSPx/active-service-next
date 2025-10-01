package com.services.active.controllers;

import com.services.active.dto.ExerciseLogResponse;
import com.services.active.models.Exercise;
import com.services.active.models.types.Category;
import com.services.active.models.types.Equipment;
import com.services.active.models.types.Level;
import com.services.active.models.types.MuscleGroup;
import com.services.active.services.ExerciseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/exercises")
@RequiredArgsConstructor
@Tag(name = "Exercises", description = "Exercise library and search endpoints")
@SecurityRequirement(name = "bearerAuth")
public class ExerciseController {
    private final ExerciseService exerciseService;

    @GetMapping("/search")
    @Operation(
        summary = "Search exercises",
        description = "Search for exercises using various filters. All parameters are optional and can be combined. " +
                "For muscle groups, exercises must contain ALL specified muscles (AND logic)."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Exercises retrieved successfully",
                content = @Content(schema = @Schema(implementation = Exercise.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token")
    })
    public List<Exercise> searchExercises(
            @Parameter(description = "Exercise name (case-insensitive partial match)")
            @RequestParam(required = false) String name,

            @Parameter(description = "Exercise category", schema = @Schema(allowableValues = {"STRENGTH", "CARDIO", "FLEXIBILITY", "SPORTS"}))
            @RequestParam(required = false) Category category,

            @Parameter(description = "Difficulty level", schema = @Schema(allowableValues = {"BEGINNER", "INTERMEDIATE", "ADVANCED"}))
            @RequestParam(required = false) Level level,

            @Parameter(description = "Primary muscle groups (comma-separated) - exercises must target ALL specified muscles",
                    example = "CHEST,SHOULDERS,TRICEPS")
            @RequestParam(required = false) List<MuscleGroup> primaryMuscles,

            @Parameter(description = "Secondary muscle groups (comma-separated) - exercises must target ALL specified muscles",
                    example = "BICEPS,FOREARMS")
            @RequestParam(required = false) List<MuscleGroup> secondaryMuscles,

            @Parameter(description = "Required equipment",
                    schema = @Schema(allowableValues = {"BARBELL", "DUMBBELL", "BODYWEIGHT", "MACHINE", "CABLE", "KETTLEBELL"}))
            @RequestParam(required = false) Equipment equipment) {

        return exerciseService.searchExercises(name, category, level, primaryMuscles, secondaryMuscles, equipment);
    }

    @GetMapping("/{exerciseId}/logs")
    @Operation(
        summary = "Get exercise logs for authenticated user",
        description = "Retrieves all exercise logs (performance records) for the authenticated user for a specific exercise, ordered by creation time (oldest first)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Exercise logs retrieved successfully",
                content = @Content(schema = @Schema(implementation = ExerciseLogResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
        @ApiResponse(responseCode = "404", description = "Exercise not found")
    })
    public List<ExerciseLogResponse> getExerciseLogs(
            @Parameter(description = "Exercise ID to get logs for", required = true)
            @PathVariable String exerciseId,
            Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return exerciseService.getExerciseLogs(principal.getName(), exerciseId);
    }
}
