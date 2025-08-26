package com.services.active.controllers;

import com.services.active.dto.UserWorkoutRecordsResponse;
import com.services.active.dto.WorkoutRecordRequest;
import com.services.active.services.WorkoutRecordService;
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
@RequestMapping("/api/workouts/record")
@RequiredArgsConstructor
@Tag(name = "Workout Records", description = "Workout record management endpoints")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class WorkoutRecordController {
    private final WorkoutRecordService workoutRecordService;

    @GetMapping
    @Operation(
        summary = "Get user's workout records",
        description = "Retrieves all workout records for the authenticated user, ordered by creation time (most recent first)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Workout records retrieved successfully",
                content = @Content(schema = @Schema(implementation = UserWorkoutRecordsResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token")
    })
    public List<UserWorkoutRecordsResponse> getUserWorkoutRecords(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return workoutRecordService.getWorkoutRecords(principal.getName());
    }

    @PostMapping
    @Operation(
        summary = "Create a workout record",
        description = "Creates a new workout record with exercise records for the authenticated user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Workout record created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
        @ApiResponse(responseCode = "404", description = "Referenced workout not found")
    })
    public ResponseEntity<String> createWorkoutRecord(
            Principal principal,
            @RequestBody @Valid WorkoutRecordRequest request) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        String workoutRecordId = workoutRecordService.createWorkoutRecord(principal.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(workoutRecordId);
    }
}
