package com.services.active.controllers;

import com.services.active.config.IntegrationTestBase;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class LegalControllerTest extends IntegrationTestBase {

    private final MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/legal/privacy-policy -> 200 OK and contains content")
    void getPrivacyPolicy_success() throws Exception {
        mockMvc.perform(get("/api/legal/privacy-policy"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Privacy Policy")));
    }

    @Test
    @DisplayName("GET /api/legal/terms-of-service -> 200 OK and contains content")
    void getTermsOfService_success() throws Exception {
        mockMvc.perform(get("/api/legal/terms-of-service"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Terms & Conditions")));
    }
}
