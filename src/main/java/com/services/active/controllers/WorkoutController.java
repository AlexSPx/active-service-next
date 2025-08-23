package com.services.active.controllers;

import com.services.active.dto.CreateWorkoutRequest;
import com.services.active.dto.UserWorkoutRecordsResponse;
import com.services.active.dto.WorkoutRecordRequest;
import com.services.active.dto.WorkoutWithTemplate;
import com.services.active.models.Workout;
import com.services.active.services.WorkoutService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;

@RestController
@RequestMapping("/api/workouts")
@RequiredArgsConstructor
@Tag(name = "Workouts", description = "Workout management endpoints")
@SecurityRequirement(name = "bearerAuth")
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
    public Mono<Workout> createWorkout(
            @Parameter(hidden = true) @AuthenticationPrincipal Mono<Principal> principalMono,
            @RequestBody  CreateWorkoutRequest request) {
        return principalMono.flatMap(principal ->
            workoutService.createWorkout(principal.getName(), request)
        );
    }

    @GetMapping
    @Operation(
        summary = "Get user's workouts",
        description = "Retrieves all workouts for the authenticated user, ordered by start time (most recent first)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Workouts retrieved successfully",
                content = @Content(schema = @Schema(implementation = Workout.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token")
    })
    public Flux<WorkoutWithTemplate> getUserWorkouts(
            @Parameter(hidden = true) @AuthenticationPrincipal Mono<Principal> principalMono) {
        return principalMono.flatMapMany(principal ->
            workoutService.getUserWorkouts(principal.getName())
        );
    }

    @GetMapping("/record")
    @Operation(
        summary = "Get user's workout records",
        description = "Retrieves all workout records for the authenticated user, ordered by creation time (most recent first)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Workout records retrieved successfully",
                content = @Content(schema = @Schema(implementation = UserWorkoutRecordsResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token")
    })
    public Flux<UserWorkoutRecordsResponse> getUserWorkoutRecords(
            @Parameter(hidden = true) @AuthenticationPrincipal Mono<Principal> principalMono) {
        return principalMono.flatMapMany(principal ->
                workoutService.getWorkoutRecords(principal.getName()));
    }


    @PostMapping("/record")
    public Mono<ResponseEntity<String>> createRecord(
            @Parameter(hidden = true) @AuthenticationPrincipal Mono<Principal> principalMono,
            @RequestBody WorkoutRecordRequest record
    ) {
        return principalMono.flatMap(principal ->
                workoutService.createWorkoutRecord(principal.getName(), record)
                        .map(result -> ResponseEntity.status(HttpStatus.CREATED).body(result)));
    }
}
