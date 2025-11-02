package com.services.active.controllers;

import com.services.active.dto.CreateRoutineRequest;
import com.services.active.dto.UpdateRoutineRequest;
import com.services.active.models.Routine;
import com.services.active.services.RoutineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/routines")
@RequiredArgsConstructor
@Tag(name = "Routines", description = "Routine management endpoints")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class RoutineController {

    private final RoutineService routineService;

    @PostMapping(produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new routine")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Routine created successfully",
                    content = @Content(schema = @Schema(implementation = Routine.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "409", description = "Duplicate routine name")
    })
    public Routine createRoutine(Principal principal, @RequestBody @Valid CreateRoutineRequest request) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return routineService.createRoutine(principal.getName(), request);
    }

    @GetMapping(produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List user's routines")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Routines retrieved successfully",
                    content = @Content(schema = @Schema(implementation = Routine.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token")
    })
    public List<Routine> listRoutines(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return routineService.listRoutines(principal.getName());
    }

    @GetMapping(value = "/{routineId}", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a routine by id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Routine retrieved successfully",
                    content = @Content(schema = @Schema(implementation = Routine.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "404", description = "Routine not found")
    })
    public Routine getRoutine(Principal principal, @PathVariable("routineId") String routineId) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return routineService.getRoutine(principal.getName(), routineId);
    }

    @PutMapping(value = "/{routineId}", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a routine")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Routine updated successfully",
                    content = @Content(schema = @Schema(implementation = Routine.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "404", description = "Routine not found"),
            @ApiResponse(responseCode = "409", description = "Duplicate routine name")
    })
    public Routine updateRoutine(Principal principal, @PathVariable("routineId") String routineId, @RequestBody UpdateRoutineRequest request) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return routineService.updateRoutine(principal.getName(), routineId, request);
    }

    @DeleteMapping(value = "/{routineId}")
    @Operation(summary = "Delete a routine")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Routine deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "404", description = "Routine not found")
    })
    public ResponseEntity<Void> deleteRoutine(Principal principal, @PathVariable("routineId") String routineId) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        routineService.deleteRoutine(principal.getName(), routineId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/active", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the active routine")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Active routine retrieved",
                    content = @Content(schema = @Schema(implementation = Routine.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "404", description = "No active routine")
    })
    public Routine getActiveRoutine(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return routineService.getActiveRoutine(principal.getName());
    }
}
