package com.services.active.services;

import com.services.active.dto.AuthRequest;
import com.services.active.dto.LoginRequest;
import com.services.active.dto.TokenResponse;
import com.services.active.exceptions.ConflictException;
import com.services.active.exceptions.UnauthorizedException;
import com.services.active.models.user.BodyMeasurements;
import com.services.active.models.user.User;
import com.services.active.models.types.AuthProvider;
import com.services.active.repository.UserRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleTokenVerifier googleTokenVerifier; // new verifier

    public TokenResponse signup(@NonNull AuthRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ConflictException("Email already exists");
        }
        BodyMeasurements measurements = null;
        if (request.getMeasurements() != null) {
            Double w = request.getMeasurements().getWeightKg();
            Integer h = request.getMeasurements().getHeightCm();
            if (w != null && w <= 0) {
                throw new com.services.active.exceptions.BadRequestException("weightKg must be > 0");
            }
            if (h != null && h <= 0) {
                throw new com.services.active.exceptions.BadRequestException("heightCm must be > 0");
            }
            if (w != null || h != null) {
                measurements = BodyMeasurements.builder().weightKg(w).heightCm(h).build();
            }
        }
        var user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .provider(AuthProvider.LOCAL)
                .createdAt(LocalDate.now())
                .timezone(Optional.ofNullable(request.getTimezone()).filter(t -> !t.isBlank()).orElse("UTC"))
                .measurements(measurements)
                .build();
        user.setNotificationPreferences(request.getNotificationFrequency());
        User saved = userRepository.save(user);
        return new TokenResponse(jwtService.generateToken(saved));
    }

    public TokenResponse login(@NonNull LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        String token = jwtService.generateToken(user);
        return new TokenResponse(token);
    }

    public TokenResponse loginWithGoogle(String idTokenString) {
        try {
            var info = googleTokenVerifier.verify(idTokenString);
            String email = info.email();
            if (email == null || email.isBlank()) {
                throw new UnauthorizedException("Email missing in token");
            }
            return userRepository.findByEmail(email)
                    .map(user -> new TokenResponse(jwtService.generateToken(user)))
                    .orElseGet(() -> {
                        User newUser = User.builder()
                                .email(email)
                                .googleId(info.googleId())
                                .username(info.name())
                                .firstName(info.givenName())
                                .lastName(info.familyName())
                                .provider(AuthProvider.GOOGLE)
                                .createdAt(LocalDate.now())
                                .timezone(null)
                                .build();
                        User saved = userRepository.save(newUser);
                        return new TokenResponse(jwtService.generateToken(saved));
                    });
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid ID token");
        }
    }
}
