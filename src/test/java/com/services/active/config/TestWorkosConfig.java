package com.services.active.config;

import com.services.active.dto.TokenResponse;
import com.services.active.models.user.WorkOSUser;
import com.services.active.services.WorkosService;
import com.workos.usermanagement.types.UpdateUserOptions;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@TestConfiguration
public class TestWorkosConfig {

    @Bean
    @Primary
    public WorkosService workosService() {
        WorkosService mockService = mock(WorkosService.class);
        
        // Mock authenticateWithCode - returns mock auth result with tokens
        when(mockService.authenticateWithCode(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            // Generate consistent test data based on code or use defaults
            return new WorkosService.WorkosAuthResult(
                    "test_workos_user_id",
                    "testuser@example.com",
                    "Test",
                    "User",
                    "mock_access_token_" + code,
                    "mock_refresh_token_" + code
            );
        });

        // Mock refreshTokens
        when(mockService.refreshTokens(anyString())).thenAnswer(invocation -> {
            String refreshToken = invocation.getArgument(0);
            return new TokenResponse(
                    "mock_new_access_token_" + refreshToken,
                    "mock_new_refresh_token_" + refreshToken
            );
        });

        // Mock getUser - returns mock user data
        when(mockService.getUser(anyString())).thenAnswer(invocation -> {
            String userId = invocation.getArgument(0);
            // Return different data based on userId for test flexibility
            if (userId.contains("test_workos")) {
                return WorkOSUser.builder()
                        .email("testuser@example.com")
                        .firstName("Test")
                        .lastName("User")
                        .build();
            }
            return WorkOSUser.builder()
                    .email("testuser@example.com")
                    .firstName("Test")
                    .lastName("User")
                    .build();
        });

        // Mock updateUser
        when(mockService.updateUser(anyString(), any(UpdateUserOptions.class))).thenAnswer(invocation -> {
            UpdateUserOptions options = invocation.getArgument(1);

            String firstName = "Test";
            String lastName = "User";

            if (options.getFirstName() != null) {
                firstName = options.getFirstName();
            }
            if (options.getLastName() != null) {
                lastName = options.getLastName();
            }

            return WorkOSUser.builder()
                    .email("testuser@example.com")
                    .firstName(firstName)
                    .lastName(lastName)
                    .build();
        });
        
        // Mock deleteUser - no-op for tests
        doNothing().when(mockService).deleteUser(anyString());

        return mockService;
    }
}

