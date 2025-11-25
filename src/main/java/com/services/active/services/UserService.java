package com.services.active.services;

import com.services.active.dto.UpdateUserRequest;
import com.services.active.exceptions.BadRequestException;
import com.services.active.exceptions.ConflictException;
import com.services.active.exceptions.NotFoundException;
import com.services.active.models.user.BodyMeasurements;
import com.services.active.models.user.User;
import com.services.active.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;

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
        if(request.getRegistrationCompleted() != null) {
            user.setRegistrationCompleted(request.getRegistrationCompleted());
        }
        if(request.getNotificationFrequency() != null) {
            user.setNotificationPreferences(request.getNotificationFrequency());
        }
        if (request.getTimezone() != null) {
            String tz = request.getTimezone();
            try {
                ZoneId.of(tz); // validate IANA id, e.g., "Europe/Sofia"
            } catch (Exception e) {
                throw new BadRequestException("Invalid timezone. Use an IANA identifier like 'Europe/Sofia'.");
            }
            user.setTimezone(tz);
        }
        if (request.getMeasurements() != null) {
            var mReq = request.getMeasurements();
            BodyMeasurements current = user.getMeasurements();
            Double newWeight = mReq.getWeightKg();
            Integer newHeight = mReq.getHeightCm();
            if (newWeight != null && newWeight <= 0) {
                throw new BadRequestException("weightKg must be > 0");
            }
            if (newHeight != null && newHeight <= 0) {
                throw new BadRequestException("heightCm must be > 0");
            }
            if (current == null) {
                if (newWeight != null || newHeight != null) {
                    current = BodyMeasurements.builder()
                            .weightKg(newWeight)
                            .heightCm(newHeight)
                            .build();
                }
            } else {
                if (newWeight != null) current.setWeightKg(newWeight);
                if (newHeight != null) current.setHeightCm(newHeight);
            }
            user.setMeasurements(current);
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
