package com.services.active.services;

import com.services.active.dto.AuthRequest;
import com.services.active.dto.BodyMeasurementsRequest;
import com.services.active.dto.LoginRequest;
import com.services.active.dto.TokenResponse;
import com.services.active.dto.UpdateUserRequest;
import com.services.active.dto.GoogleUserInfo;
import com.services.active.exceptions.ConflictException;
import com.services.active.exceptions.UnauthorizedException;
import com.services.active.models.BodyMeasurements;
import com.services.active.models.User;
import com.services.active.models.types.AuthProvider;
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
    @Mock
    private StreakService streakService;
    @Mock
    private GoogleTokenVerifier googleTokenVerifier;

    private AuthService authService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, googleTokenVerifier);
        userService = new UserService(userRepository, streakService);
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

    @Test
    void signup_shouldStoreMeasurementsWhenProvided() {
        AuthRequest request = new AuthRequest();
        request.setUsername("measureuser");
        request.setEmail("measure@example.com");
        request.setFirstName("Measure");
        request.setLastName("User");
        request.setPassword("password");
        var measurements = new com.services.active.dto.BodyMeasurementsRequest();
        measurements.setWeightKg(81.3);
        measurements.setHeightCm(182);
        request.setMeasurements(measurements);
        when(userRepository.findByEmail("measure@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("hashed");
        when(userRepository.save(ArgumentMatchers.any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(ArgumentMatchers.any(User.class))).thenReturn("token");

        authService.signup(request);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertNotNull(captor.getValue().getMeasurements());
        assertEquals(81.3, captor.getValue().getMeasurements().getWeightKg());
        assertEquals(182, captor.getValue().getMeasurements().getHeightCm());
    }

    @Test
    void signup_shouldRejectInvalidMeasurements() {
        AuthRequest request = new AuthRequest();
        request.setUsername("badmeasure");
        request.setEmail("badmeasure@example.com");
        request.setFirstName("Bad");
        request.setLastName("Measure");
        request.setPassword("password");
        var measurements = new com.services.active.dto.BodyMeasurementsRequest();
        measurements.setWeightKg(-10.0); // invalid
        request.setMeasurements(measurements);
        when(userRepository.findByEmail("badmeasure@example.com")).thenReturn(Optional.empty());

        var ex = assertThrows(com.services.active.exceptions.BadRequestException.class, () -> authService.signup(request));
        assertEquals("weightKg must be > 0", ex.getMessage());
    }

    @Test
    void updateUser_shouldCreateMeasurementsWhenAbsent() {
        User user = User.builder().id("u1").build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(userRepository.save(ArgumentMatchers.any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        BodyMeasurementsRequest mReq = BodyMeasurementsRequest.builder().weightKg(70.2).heightCm(175).build();
        UpdateUserRequest update = UpdateUserRequest.builder().measurements(mReq).build();

        User updated = userService.updateUser("u1", update);
        assertNotNull(updated.getMeasurements());
        assertEquals(70.2, updated.getMeasurements().getWeightKg());
        assertEquals(175, updated.getMeasurements().getHeightCm());
    }

    @Test
    void updateUser_shouldMergeMeasurementsWhenPresent() {
        User user = User.builder().id("u2").measurements(BodyMeasurements.builder().weightKg(80.0).heightCm(180).build()).build();
        when(userRepository.findById("u2")).thenReturn(Optional.of(user));
        when(userRepository.save(ArgumentMatchers.any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        BodyMeasurementsRequest mReq = BodyMeasurementsRequest.builder().weightKg(78.5).build(); // only weight
        UpdateUserRequest update = UpdateUserRequest.builder().measurements(mReq).build();

        User updated = userService.updateUser("u2", update);
        assertEquals(78.5, updated.getMeasurements().getWeightKg());
        assertEquals(180, updated.getMeasurements().getHeightCm()); // unchanged
    }

    @Test
    void updateUser_shouldRejectInvalidNegativeWeight() {
        User user = User.builder().id("u3").build();
        when(userRepository.findById("u3")).thenReturn(Optional.of(user));

        BodyMeasurementsRequest mReq = BodyMeasurementsRequest.builder().weightKg(-5.0).build();
        UpdateUserRequest update = UpdateUserRequest.builder().measurements(mReq).build();

        var ex = assertThrows(com.services.active.exceptions.BadRequestException.class, () -> userService.updateUser("u3", update));
        assertEquals("weightKg must be > 0", ex.getMessage());
    }

    @Test
    void updateUser_shouldRejectInvalidNegativeHeight() {
        User user = User.builder().id("u4").build();
        when(userRepository.findById("u4")).thenReturn(Optional.of(user));

        BodyMeasurementsRequest mReq = BodyMeasurementsRequest.builder().heightCm(-180).build();
        UpdateUserRequest update = UpdateUserRequest.builder().measurements(mReq).build();

        var ex = assertThrows(com.services.active.exceptions.BadRequestException.class, () -> userService.updateUser("u4", update));
        assertEquals("heightCm must be > 0", ex.getMessage());
    }

    // GOOGLE LOGIN TESTS
    @Test
    void googleLogin_existingUserReturnsToken() {
        String idToken = "dummy";
        when(googleTokenVerifier.verify(idToken)).thenReturn(new GoogleUserInfo("gid1", "user@example.com", "Test User", null, "New", "User"));
        User existing = User.builder().email("user@example.com").provider(AuthProvider.GOOGLE).build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));
        when(jwtService.generateToken(existing)).thenReturn("token123");

        TokenResponse resp = authService.loginWithGoogle(idToken);
        assertEquals("token123", resp.getToken());
        verify(userRepository, never()).save(any());
    }

    @Test
    void googleLogin_newUserCreatedWhenNotFound() {
        String idToken = "dummy2";
        when(googleTokenVerifier.verify(idToken)).thenReturn(new GoogleUserInfo("gid2", "newuser@example.com", "newuser", null, "New", "User"));
        when(userRepository.findByEmail("newuser@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("tokenNew");

        TokenResponse resp = authService.loginWithGoogle(idToken);
        assertEquals("tokenNew", resp.getToken());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("newuser", saved.getUsername());
        assertEquals("New", saved.getFirstName());
        assertEquals("User", saved.getLastName());
        assertEquals(AuthProvider.GOOGLE, saved.getProvider());
    }

    @Test
    void googleLogin_missingEmailFails() {
        String idToken = "dummy3";
        when(googleTokenVerifier.verify(idToken)).thenReturn(new GoogleUserInfo("gid3", null, "No Email", null, "New", "User"));
        var ex = assertThrows(UnauthorizedException.class, () -> authService.loginWithGoogle(idToken));
        assertEquals("Email missing in token", ex.getMessage());
    }

    @Test
    void googleLogin_verifierThrowsUnauthorizedMapped() {
        String idToken = "bad";
        when(googleTokenVerifier.verify(idToken)).thenThrow(new RuntimeException("boom"));
        var ex = assertThrows(UnauthorizedException.class, () -> authService.loginWithGoogle(idToken));
        assertEquals("Invalid ID token", ex.getMessage());
    }
}
