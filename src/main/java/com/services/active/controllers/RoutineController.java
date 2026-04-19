package com.services.active.controllers;

import com.services.active.dto.CreateRoutineRequest;
import com.services.active.dto.RoutineResponse;
import com.services.active.dto.UpdateRoutineRequest;
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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
                    content = @Content(schema = @Schema(implementation = RoutineResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "409", description = "Duplicate routine name")
    })
    public RoutineResponse createRoutine(Principal principal, @RequestBody @Valid CreateRoutineRequest request) {
        if (principal == null) {
            log.warn("Rejecting routine creation request because principal is missing");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        log.info("Received routine creation request (name={})", request.getName());
        return routineService.createRoutine(principal.getName(), request);
    }

    @GetMapping(produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List user's routines")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Routines retrieved successfully",
                    content = @Content(schema = @Schema(implementation = RoutineResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token")
    })
    public List<RoutineResponse> listRoutines(Principal principal) {
        if (principal == null) {
            log.warn("Rejecting routine listing request because principal is missing");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        log.info("Received routine listing request");
        return routineService.listRoutines(principal.getName());
    }

    @GetMapping(value = "/{routineId}", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a routine by id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Routine retrieved successfully",
                    content = @Content(schema = @Schema(implementation = RoutineResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "404", description = "Routine not found")
    })
    public RoutineResponse getRoutine(Principal principal, @PathVariable("routineId") String routineId) {
        if (principal == null) {
            log.warn("Rejecting routine fetch request because principal is missing (routineId={})", routineId);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        log.info("Received routine fetch request (routineId={})", routineId);
        return routineService.getRoutine(principal.getName(), routineId);
    }

    @PutMapping(value = "/{routineId}", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a routine")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Routine updated successfully",
                    content = @Content(schema = @Schema(implementation = RoutineResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "404", description = "Routine not found"),
            @ApiResponse(responseCode = "409", description = "Duplicate routine name")
    })
    public RoutineResponse updateRoutine(Principal principal, @PathVariable("routineId") String routineId, @RequestBody UpdateRoutineRequest request) {
        if (principal == null) {
            log.warn("Rejecting routine update request because principal is missing (routineId={})", routineId);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        log.info("Received routine update request (routineId={})", routineId);
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
            log.warn("Rejecting routine deletion request because principal is missing (routineId={})", routineId);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        log.info("Received routine deletion request (routineId={})", routineId);
        routineService.deleteRoutine(principal.getName(), routineId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/active", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the active routine")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Active routine retrieved",
                    content = @Content(schema = @Schema(implementation = RoutineResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "404", description = "No active routine")
    })
    public RoutineResponse getActiveRoutine(Principal principal) {
        if (principal == null) {
            log.warn("Rejecting active routine fetch request because principal is missing");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        log.info("Received active routine fetch request");
        return routineService.getActiveRoutine(principal.getName());
    }
}
