package com.services.active.controllers;

import com.services.active.config.IntegrationTestBase;
import com.services.active.config.user.TestUserToken;
import com.services.active.config.user.WithTestUser;
import com.services.active.dto.CreateWorkoutRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@WithTestUser
@SpringBootTest
@AutoConfigureWebTestClient
class WorkoutControllerTest extends IntegrationTestBase {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("POST /api/workouts -> 201 CREATED returns workout on success")
    void createWorkout_success(@TestUserToken String token) {
        CreateWorkoutRequest request = CreateWorkoutRequest.builder()
                .title("Push Day Workout")
                .notes("Focus on form and progressive overload")
                .build();

        webTestClient.post()
                .uri("/api/workouts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id").isNotEmpty()
                .jsonPath("$.title").isEqualTo("Push Day Workout");
    }
}
