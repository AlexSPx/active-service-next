package com.services.active.services;

import com.services.active.dto.AuthRequest;
import com.services.active.dto.LoginRequest;
import com.services.active.dto.TokenResponse;
import com.services.active.models.User;
import com.services.active.models.types.AuthProvider;
import com.services.active.repository.UserRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public Mono<ResponseEntity<TokenResponse>> signup(@NonNull AuthRequest request) {
        return userRepository.findByEmail(request.getEmail())
                .flatMap(_ -> Mono.<ResponseEntity<TokenResponse>>error(
                        new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists")))
                .switchIfEmpty(Mono.defer(() -> {
                    var user = User.builder()
                            .username(request.getUsername())
                            .email(request.getEmail())
                            .firstName(request.getFirstName())
                            .lastName(request.getLastName())
                            .passwordHash(passwordEncoder.encode(request.getPassword()))
                            .provider(AuthProvider.LOCAL)
                            .createdAt(LocalDate.now())
                            .build();

                    return userRepository.save(user)
                            .map(saved -> ResponseEntity.ok(new TokenResponse(jwtService.generateToken(saved))));
                }));
    }

    public Mono<ResponseEntity<TokenResponse>> login(@NonNull LoginRequest request) {
        return userRepository.findByEmail(request.getEmail())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
                    }

                    String token = jwtService.generateToken(user);
                    return Mono.just(ResponseEntity.ok(new TokenResponse(token)));
                });
    }
}

