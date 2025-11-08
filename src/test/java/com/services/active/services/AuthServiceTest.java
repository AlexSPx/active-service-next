package com.services.active.services;

import com.services.active.dto.AuthRequest;
import com.services.active.dto.LoginRequest;
import com.services.active.dto.TokenResponse;
import com.services.active.exceptions.ConflictException;
import com.services.active.exceptions.UnauthorizedException;
import com.services.active.models.User;
import com.services.active.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void signup_shouldReturnConflictIfEmailExists() {
        AuthRequest request = new AuthRequest();
        request.setEmail("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(new User()));

        ConflictException ex = assertThrows(ConflictException.class, () -> authService.signup(request));
        assertEquals("Email already exists", ex.getMessage());
    }

    @Test
    void signup_shouldCreateUserIfEmailNotExists() {
        AuthRequest request = new AuthRequest();
        request.setUsername("testuser");
        request.setEmail("new@example.com");
        request.setFirstName("Test");
        request.setLastName("User");
        request.setPassword("password");
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("hashed");
        when(userRepository.save(ArgumentMatchers.any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(ArgumentMatchers.any(User.class))).thenReturn("token");

        TokenResponse response = authService.signup(request);
        assertNotNull(response);
        assertEquals("token", response.getToken());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("testuser", saved.getUsername());
        assertEquals("new@example.com", saved.getEmail());
        assertEquals("Test", saved.getFirstName());
        assertEquals("User", saved.getLastName());
        assertNotNull(saved.getCreatedAt());
        assertEquals(LocalDate.now(), saved.getCreatedAt());
        assertEquals("UTC", saved.getTimezone());
    }

    @Test
    void signup_shouldSetDefaultTimezoneWhenMissing() {
        AuthRequest request = new AuthRequest();
        request.setUsername("tzuser");
        request.setEmail("tz@example.com");
        request.setFirstName("Tz");
        request.setLastName("User");
        request.setPassword("password");
        when(userRepository.findByEmail("tz@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("hashed");
        when(userRepository.save(ArgumentMatchers.any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(ArgumentMatchers.any(User.class))).thenReturn("token");

        authService.signup(request);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("UTC", captor.getValue().getTimezone());
    }

    @Test
    void signup_shouldRespectProvidedTimezone() {
        AuthRequest request = new AuthRequest();
        request.setUsername("tzuser2");
        request.setEmail("tz2@example.com");
        request.setFirstName("Tz2");
        request.setLastName("User");
        request.setPassword("password");
        request.setTimezone("America/New_York");
        when(userRepository.findByEmail("tz2@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("hashed");
        when(userRepository.save(ArgumentMatchers.any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(ArgumentMatchers.any(User.class))).thenReturn("token");

        authService.signup(request);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("America/New_York", captor.getValue().getTimezone());
    }

    @Test
    void login_shouldReturnUnauthorizedIfUserNotFound() {
        LoginRequest request = new LoginRequest();
        request.setEmail("notfound@example.com");
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> authService.login(request));
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void login_shouldReturnUnauthorizedIfPasswordInvalid() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("wrong");
        User user = User.builder().passwordHash("hashed").build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> authService.login(request));
        assertEquals("Invalid credentials", ex.getMessage());
    }

    @Test
    void login_shouldReturnTokenIfCredentialsValid() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("correct");
        User user = User.builder().passwordHash("hashed").build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("token");

        TokenResponse response = authService.login(request);
        assertNotNull(response);
        assertEquals("token", response.getToken());
    }

    @Test
    void signup_blankTimezoneDefaultsToUTC() {
        AuthRequest request = new AuthRequest();
        request.setUsername("tzblank");
        request.setEmail("tzblank@example.com");
        request.setFirstName("Tz");
        request.setLastName("Blank");
        request.setPassword("password");
        request.setTimezone("   ");
        when(userRepository.findByEmail("tzblank@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("hashed");
        when(userRepository.save(ArgumentMatchers.any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(ArgumentMatchers.any(User.class))).thenReturn("token");

        authService.signup(request);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("UTC", captor.getValue().getTimezone());
    }
}
