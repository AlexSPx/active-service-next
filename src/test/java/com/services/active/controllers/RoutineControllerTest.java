package com.services.active.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.services.active.config.IntegrationTestBase;
import com.services.active.config.user.TestUserContext;
import com.services.active.config.user.WithTestUser;
import com.services.active.dto.CreateRoutineRequest;
import com.services.active.dto.UpdateRoutineRequest;
import com.services.active.models.RoutinePattern;
import com.services.active.models.types.DayType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithTestUser
@SpringBootTest
@AutoConfigureMockMvc
class RoutineControllerTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CreateRoutineRequest buildRequest(String name, boolean active) {
        RoutinePattern d1 = RoutinePattern.builder().dayIndex(1).dayType(DayType.WORKOUT).workoutId("w1").build();
        RoutinePattern d2 = RoutinePattern.builder().dayIndex(2).dayType(DayType.REST).workoutId(null).build();
        return CreateRoutineRequest.builder()
                .name(name)
                .description("desc")
                .pattern(List.of(d1, d2))
                .active(active)
                .build();
    }

    @Test
    @DisplayName("POST /api/routines -> 400 BAD REQUEST when name is missing")
    void createRoutine_missingName_badRequest(@TestUserContext String token) throws Exception {
        CreateRoutineRequest req = buildRequest(null, false);
        mockMvc.perform(post("/api/routines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Name is required"));
    }

    @Test
    @DisplayName("POST /api/routines -> 400 BAD REQUEST when pattern is missing")
    void createRoutine_missingPattern_badRequest(@TestUserContext String token) throws Exception {
        CreateRoutineRequest req = CreateRoutineRequest.builder()
                .name("My Routine")
                .description("desc")
                .build();
        mockMvc.perform(post("/api/routines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Pattern is required"));
    }

    @Test
    @DisplayName("POST /api/routines -> 201 CREATED returns routine and sets active when requested")
    void createRoutine_success_created_and_active(@TestUserContext String token) throws Exception {
        CreateRoutineRequest req = buildRequest("PPL", true);
        mockMvc.perform(post("/api/routines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("PPL"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.pattern", hasSize(2)));

        mockMvc.perform(get("/api/routines/active")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("PPL"));
    }

    @Test
    @DisplayName("GET /api/routines -> 200 returns list of user routines")
    void listRoutines_returnsItems(@TestUserContext String token) throws Exception {
        mockMvc.perform(post("/api/routines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("A", false))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/routines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("B", false))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/routines")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("PUT /api/routines/{id} -> 409 when renaming to existing name")
    void updateRoutine_renameConflict(@TestUserContext String token) throws Exception {
        String r1 = mockMvc.perform(post("/api/routines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Alpha", false))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/routines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Beta", false))))
                .andExpect(status().isCreated());

        String id1 = new ObjectMapper().readTree(r1).get("id").asText();

        UpdateRoutineRequest renameToBeta = UpdateRoutineRequest.builder().name("Beta").build();
        mockMvc.perform(put("/api/routines/" + id1)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(renameToBeta)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Routine name already exists"));
    }

    @Test
    @DisplayName("PUT /api/routines/{id} -> activate/deactivate updates user's active pointer")
    void updateRoutine_toggleActive(@TestUserContext String token) throws Exception {
        mockMvc.perform(post("/api/routines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("One", true))))
                .andExpect(status().isCreated());
        String created2 = mockMvc.perform(post("/api/routines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Two", false))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id2 = new ObjectMapper().readTree(created2).get("id").asText();

        UpdateRoutineRequest activate = UpdateRoutineRequest.builder().active(true).build();
        mockMvc.perform(put("/api/routines/" + id2)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activate)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/routines/active").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Two"));

        UpdateRoutineRequest deactivate = UpdateRoutineRequest.builder().active(false).build();
        mockMvc.perform(put("/api/routines/" + id2)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactivate)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/routines/active").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No active routine"));
    }

    @Test
    @DisplayName("DELETE /api/routines/{id} -> 204 and clears active when deleting active routine")
    void deleteRoutine_clearsActive(@TestUserContext String token) throws Exception {
        String created = mockMvc.perform(post("/api/routines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Solo", true))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = new ObjectMapper().readTree(created).get("id").asText();

        mockMvc.perform(delete("/api/routines/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/routines/active").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No active routine"));

        mockMvc.perform(get("/api/routines/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
