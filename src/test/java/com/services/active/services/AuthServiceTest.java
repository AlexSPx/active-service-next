package com.services.active.services;

import com.services.active.dto.AuthRequest;
import com.services.active.dto.LoginRequest;
import com.services.active.dto.TokenResponse;
import com.services.active.models.User;
import com.services.active.models.types.AuthProvider;
import com.services.active.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void signup_shouldReturnConflictIfEmailExists() {
        AuthRequest request = new AuthRequest();
        request.setEmail("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Mono.just(new User()));

        Mono<TokenResponse> result = authService.signup(request);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, result::block);
        assertEquals("Email already exists", ex.getReason());
    }

    @Test
    void signup_shouldCreateUserIfEmailNotExists() {
        AuthRequest request = new AuthRequest();
        request.setUsername("testuser");
        request.setEmail("new@example.com");
        request.setFirstName("Test");
        request.setLastName("User");
        request.setPassword("password");
        when(userRepository.findByEmail("new@example.com")).thenReturn(Mono.empty());
        when(passwordEncoder.encode("password")).thenReturn("hashed");
        User user = User.builder()
                .username("testuser")
                .email("new@example.com")
                .firstName("Test")
                .lastName("User")
                .passwordHash("hashed")
                .provider(AuthProvider.LOCAL)
                .createdAt(LocalDate.now())
                .build();
        when(userRepository.save(ArgumentMatchers.any(User.class))).thenReturn(Mono.just(user));
        when(jwtService.generateToken(user)).thenReturn("token");

        Mono<TokenResponse> result = authService.signup(request);
        TokenResponse response = result.block();
        assertNotNull(response);
        assertEquals("token", response.getToken());
    }

    @Test
    void login_shouldReturnUnauthorizedIfUserNotFound() {
        LoginRequest request = new LoginRequest();
        request.setEmail("notfound@example.com");
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Mono.empty());

        Mono<TokenResponse> result = authService.login(request);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, result::block);
        assertEquals("User not found", ex.getReason());
    }

    @Test
    void login_shouldReturnUnauthorizedIfPasswordInvalid() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("wrong");
        User user = User.builder().passwordHash("hashed").build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        Mono<TokenResponse> result = authService.login(request);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, result::block);
        assertEquals("Invalid credentials", ex.getReason());
    }

    @Test
    void login_shouldReturnTokenIfCredentialsValid() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("correct");
        User user = User.builder().passwordHash("hashed").build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("token");

        Mono<TokenResponse> result = authService.login(request);
        TokenResponse response = result.block();
        assertNotNull(response);
        assertEquals("token", response.getToken());
    }
}
