package com.services.active.services;

import com.services.active.dto.TokenResponse;
import com.services.active.models.user.WorkOSUser;
import com.workos.WorkOS;
import com.services.active.exceptions.UnauthorizedException;
import com.workos.usermanagement.models.Authentication;
import com.workos.usermanagement.models.RefreshAuthentication;
import com.workos.usermanagement.models.User;
import com.workos.usermanagement.types.UpdateUserOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WorkosService {

    private final WorkOS workos;
    private final String clientId;

    public WorkosService(
            @Value("${workos.api-key}") String apiKey,
            @Value("${workos.client-id}") String clientId) {
        this.workos = new WorkOS(apiKey);
        this.clientId = clientId;
    }

    /**
     * Authenticate user with WorkOS authorization code
     * @param code The authorization code from WorkOS
     * @return Authenticated user information from WorkOS
     */
    public WorkosAuthResult authenticateWithCode(String code) {
        try {
            Authentication response = workos.userManagement.authenticateWithCode(clientId, code, null);
            if(response.getUser() == null) {
                log.error("WorkOS authentication response or user is null");
                throw new UnauthorizedException("Invalid authentication code");
            }

            return new WorkosAuthResult(
                    response.getUser().getId(),
                    response.getUser().getEmail(),
                    response.getUser().getFirstName(),
                    response.getUser().getLastName(),
                    response.getAccessToken(),
                    response.getRefreshToken()
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
}
