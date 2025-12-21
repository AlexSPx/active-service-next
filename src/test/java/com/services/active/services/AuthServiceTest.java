package com.services.active.services;

import com.services.active.dto.TokenResponse;
import com.services.active.exceptions.UnauthorizedException;
import com.services.active.models.user.User;
import com.services.active.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkosService workosService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Common setup if needed
    }

    @Test
    @DisplayName("authenticateWithWorkos -> creates new user on first login")
    void testAuthenticateWithWorkos_NewUser() {
        // Given
        String code = "auth_code_123";
        String workosUserId = "user_workos_123";
        String email = "newuser@example.com";

        WorkosService.WorkosAuthResult authResult = new WorkosService.WorkosAuthResult(
                workosUserId, email, "New", "User", "access_token_123", "refresh_token_123");
        when(workosService.authenticateWithCode(code)).thenReturn(authResult);
        when(userRepository.findByWorkosId(workosUserId)).thenReturn(Optional.empty());

        User newUser = User.builder()
                .id("user_mongo_123")
                .workosId(workosUserId)
                .createdAt(LocalDate.now())
                .timezone("UTC")
                .build();
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        // When
        TokenResponse response = authService.authenticateWithWorkos(code);

        // Then
        assertNotNull(response);
        assertEquals("access_token_123", response.getToken());
        assertEquals("refresh_token_123", response.getRefreshToken());
        verify(workosService).authenticateWithCode(code);
        verify(userRepository).findByWorkosId(workosUserId);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("authenticateWithWorkos -> returns existing user on subsequent login")
    void testAuthenticateWithWorkos_ExistingUser() {
        // Given
        String code = "auth_code_456";
        String workosUserId = "user_workos_456";
        String email = "existinguser@example.com";

        WorkosService.WorkosAuthResult authResult = new WorkosService.WorkosAuthResult(
                workosUserId, email, "Existing", "User", "access_token_456", "refresh_token_456");
        when(workosService.authenticateWithCode(code)).thenReturn(authResult);

        User existingUser = User.builder()
                .id("user_mongo_456")
                .workosId(workosUserId)
                .createdAt(LocalDate.now().minusDays(30))
                .timezone("America/New_York")
                .build();
        when(userRepository.findByWorkosId(workosUserId)).thenReturn(Optional.of(existingUser));

        // When
        TokenResponse response = authService.authenticateWithWorkos(code);

        // Then
        assertNotNull(response);
        assertEquals("access_token_456", response.getToken());
        assertEquals("refresh_token_456", response.getRefreshToken());
        verify(workosService).authenticateWithCode(code);
        verify(userRepository).findByWorkosId(workosUserId);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("authenticateWithWorkos -> throws exception on invalid code")
    void testAuthenticateWithWorkos_InvalidCode() {
        // Given
        String invalidCode = "invalid_code";
        when(workosService.authenticateWithCode(invalidCode))
                .thenThrow(new UnauthorizedException("Invalid authentication code"));

        // When & Then
        assertThrows(UnauthorizedException.class,
                () -> authService.authenticateWithWorkos(invalidCode));
        verify(workosService).authenticateWithCode(invalidCode);
        verify(userRepository, never()).save(any(User.class));
    }
}

