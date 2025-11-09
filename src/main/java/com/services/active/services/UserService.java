package com.services.active.services;

import com.services.active.dto.UpdateUserRequest;
import com.services.active.exceptions.BadRequestException;
import com.services.active.exceptions.ConflictException;
import com.services.active.exceptions.NotFoundException;
import com.services.active.models.User;
import com.services.active.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final StreakService streakService;

    public User getUserById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        streakService.checkStreak(user);
        return user;
    }

    public User updateUser(String userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (request.getEmail() != null) {
            String newEmail = request.getEmail();
            if (!newEmail.equals(user.getEmail())) {
                userRepository.findByEmail(newEmail)
                        .filter(found -> !found.getId().equals(userId))
                        .ifPresent(found -> { throw new ConflictException("Email already exists"); });
                user.setEmail(newEmail);
            }
        }
        if (request.getUsername() != null) {
            user.setUsername(request.getUsername());
        }
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getTimezone() != null) {
            user.setTimezone(request.getTimezone());
        }
        return userRepository.save(user);
    }

    public User registerPushToken(String userId, String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new BadRequestException("Token is required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (!user.getPushTokens().contains(token)) {
            user.getPushTokens().add(token);
            user = userRepository.save(user);
        }
        return user;
    }
}
