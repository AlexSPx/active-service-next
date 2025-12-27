package com.services.active.services;

import com.services.active.dto.TokenResponse;
import com.services.active.models.user.User;
import com.services.active.repository.UserRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final WorkosService workosService;

    /**
     * Authenticate user with WorkOS authorization code
     * Creates new user if first time login, otherwise returns existing user
     * Returns WorkOS JWT tokens directly
     */
    public TokenResponse authenticateWithWorkos(@NonNull String code) {
        WorkosService.WorkosAuthResult authResult = workosService.authenticateWithCode(code);

        String workosUserId = authResult.userId();

        User user = userRepository.findByWorkosId(workosUserId)
                .orElseGet(() -> createUser(authResult));

        if(user.getWorkosId() == null) {
            log.error("User creation failed for WorkOS ID {}", workosUserId);
            throw new RuntimeException("Failed to create user with WorkOS ID");
        }

        return new TokenResponse(authResult.accessToken(), authResult.refreshToken());
    }

    private User createUser(WorkosService.WorkosAuthResult authResult) {
        log.info("Creating new user for WorkOS ID {}", authResult.userId());

        User newUser = User.builder()
                .workosId(authResult.userId())
                .timezone("UTC")
                .build();
        return userRepository.save(newUser);
    }
}
