package com.services.active.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.services.active.dto.TokenResponse;
import com.services.active.exceptions.UnauthorizedException;
import com.services.active.models.user.WorkOSUser;
import com.workos.WorkOS;
import com.workos.usermanagement.models.RefreshAuthentication;
import com.workos.usermanagement.models.User;
import com.workos.usermanagement.types.UpdateUserOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class WorkosService {

    private final WorkOS workos;
    private final String apiKey;
    private final String clientId;
    private final RestClient restClient;

    public WorkosService(
            @Value("${workos.api-key}") String apiKey,
            @Value("${workos.client-id}") String clientId,
            @Value("${workos.base-url:https://api.workos.com}") String workosBaseUrl) {
        this.workos = new WorkOS(apiKey);
        this.apiKey = apiKey;
        this.clientId = clientId;
        this.restClient = RestClient.builder()
                .baseUrl(workosBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Authenticate user with WorkOS authorization code
     * @param code The authorization code from WorkOS
     * @return Authenticated user information from WorkOS
     */
    public WorkosAuthResult authenticateWithCode(String code) {
        try {
            AuthenticationResponse response = restClient.post()
                    .uri("/user_management/authenticate")
                    .body(new AuthenticationWithCodeRequest(clientId, apiKey, "authorization_code", code))
                    .retrieve()
                    .body(AuthenticationResponse.class);

            if (response == null || response.user() == null) {
                log.error("WorkOS authentication response or user is null");
                throw new UnauthorizedException("Invalid authentication code");
            }

            return new WorkosAuthResult(
                    response.user().id(),
                    response.user().email(),
                    response.user().firstName(),
                    response.user().lastName(),
                    response.accessToken(),
                    response.refreshToken()
            );
        } catch (Exception e) {
            log.error("Failed to authenticate with WorkOS code: {}", e.getMessage(), e);
            throw new UnauthorizedException("Invalid authentication code");
        }
    }

    public TokenResponse refreshTokens(String refreshToken) {
        try {
            RefreshAuthentication response = workos.userManagement
                    .authenticateWithRefreshToken(clientId, refreshToken, null, null);

            return new TokenResponse(response.getAccessToken(), response.getRefreshToken());
        } catch (Exception e) {
            log.error("Failed to refresh WorkOS tokens: {}", e.getMessage(), e);
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
    }

    /**
     * Get user information from WorkOS by user ID
     * @param userId The WorkOS user ID
     * @return User object from WorkOS
     */
    public WorkOSUser getUser(String userId) {
        try {
            User user = workos.userManagement.getUser(userId);
            return WorkOSUser.builder()
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .build();
        } catch (Exception e) {
            log.error("Failed to get user from WorkOS userId {}: {}", userId, e.getMessage(), e);
            throw new UnauthorizedException("Failed to retrieve user information");
        }
    }

    /**
     * Update user information in WorkOS
     * @param userId The WorkOS user ID
     * @param updateUserOptions Options for updating the user
     * @return Updated WorkOSUser object
     */
    public WorkOSUser updateUser(String userId, UpdateUserOptions updateUserOptions) {
        try {
            User user = workos.userManagement.updateUser(userId, updateUserOptions);

            return WorkOSUser.builder()
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .build();
        } catch (Exception e) {
            log.error("Failed to update user in WorkOS: {}", e.getMessage(), e);
            throw new UnauthorizedException("Failed to update user information");
        }
    }

    public void deleteUser(String workosUserId) {
        try {
            log.info("Deleting workos user: {}", workosUserId);
            workos.userManagement.deleteUser(workosUserId);
        } catch (Exception e) {
            log.error("Failed to delete user in WorkOS: {}", e.getMessage(), e);
            throw new UnauthorizedException("Failed to delete user");
        }
    }

    /**
     * Data class to hold WorkOS authentication result including tokens
     */
    public record WorkosAuthResult(String userId, String email, String firstName, String lastName, String accessToken, String refreshToken) {}

    private record AuthenticationWithCodeRequest(
            @JsonProperty("client_id") String clientId,
            @JsonProperty("client_secret") String clientSecret,
            @JsonProperty("grant_type") String grantType,
            @JsonProperty("code") String code) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AuthenticationResponse(
            @JsonProperty("user") AuthenticationUser user,
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AuthenticationUser(
            @JsonProperty("id") String id,
            @JsonProperty("email") String email,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName) {}
}
