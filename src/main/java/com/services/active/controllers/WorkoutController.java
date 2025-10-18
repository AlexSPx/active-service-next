package com.services.active.controllers;

import com.services.active.dto.CreateWorkoutRequest;
import com.services.active.dto.UserWorkoutResponse;
import com.services.active.models.Workout;
import com.services.active.services.WorkoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/workouts")
@RequiredArgsConstructor
@Tag(name = "Workouts", description = "Workout management endpoints")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class WorkoutController {
    private final WorkoutService workoutService;

    @PostMapping(produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a new workout",
        description = "Creates a new workout session. Can optionally create a new workout template or reference an existing one."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Workout created successfully",
                content = @Content(schema = @Schema(implementation = Workout.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
        @ApiResponse(responseCode = "404", description = "Referenced template not found")
    })
    public Workout createWorkout(
            Principal principal,
            @RequestBody @Valid CreateWorkoutRequest request) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return workoutService.createWorkout(principal.getName(), request);
    }

    @GetMapping
    @Operation(
        summary = "Get user's workouts",
        description = "Retrieves all workouts for the authenticated user, ordered by start time (most recent first)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Workouts retrieved successfully",
                content = @Content(schema = @Schema(implementation = UserWorkoutResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token")
    })
    public List<UserWorkoutResponse> getUserWorkouts(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return workoutService.getUserWorkouts(principal.getName());
    }

    @PutMapping(value = "/{workoutId}", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Update a workout",
        description = "Partially updates a workout. Only provided fields are updated. For template, exercises are updated only if provided and non-empty."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Workout updated successfully",
                content = @Content(schema = @Schema(implementation = Workout.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
        @ApiResponse(responseCode = "404", description = "Workout or template not found")
    })
    public Workout updateWorkout(
            Principal principal,
            @PathVariable("workoutId") String workoutId,
            @RequestBody com.services.active.dto.UpdateWorkoutRequest request) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return workoutService.updateWorkout(principal.getName(), workoutId, request);
    }

    @DeleteMapping(value = "/{workoutId}")
    @Operation(
        summary = "Delete a workout and its template",
        description = "Deletes the workout and the associated template. Workout records are NOT deleted."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Workout deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
        @ApiResponse(responseCode = "404", description = "Workout not found")
    })
    public ResponseEntity<Void> deleteWorkout(
            Principal principal,
            @PathVariable("workoutId") String workoutId) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        workoutService.deleteWorkout(principal.getName(), workoutId);
        return ResponseEntity.noContent().build();
    }
}
