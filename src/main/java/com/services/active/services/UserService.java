package com.services.active.services;

import com.services.active.dto.UpdateUserRequest;
import com.services.active.exceptions.BadRequestException;
import com.services.active.exceptions.ConflictException;
import com.services.active.exceptions.NotFoundException;
import com.services.active.models.Workout;
import com.services.active.models.user.BodyMeasurements;
import com.services.active.models.user.FullUser;
import com.services.active.models.user.User;
import com.services.active.models.user.WorkOSUser;
import com.services.active.repository.*;
import com.workos.usermanagement.builders.UpdateUserOptionsBuilder;
import com.workos.usermanagement.types.UpdateUserOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final StreakService streakService;
    private final WorkoutRepository workoutRepository;
    private final WorkoutTemplateRepository workoutTemplateRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final ExercisePersonalBestRepository exercisePersonalBestRepository;
    private final RoutineRepository routineRepository;

    private final WorkosService workosService;

    public FullUser getUserById(String workosId) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        streakService.checkStreak(user);

        WorkOSUser workOSUser = workosService.getUser(workosId);

        return FullUser.from(user, workOSUser);
    }

    public FullUser updateUser(String workosId, UpdateUserRequest request) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        UpdateUserOptionsBuilder workosUpdateBuilder = UpdateUserOptionsBuilder.create(workosId);

        if(request.getFirstName() != null) {
            workosUpdateBuilder.setFirstName(request.getFirstName());
        }

        if(request.getLastName() != null) {
            workosUpdateBuilder.setLastName(request.getLastName());
        }

        if(request.getUsername() != null) {
            user.setUsername(request.getUsername());
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

        WorkOSUser workOSUser = workosService.updateUser(workosId, workosUpdateBuilder.build());
        User dbUser =  userRepository.save(user);

        return FullUser.from(dbUser, workOSUser);
    }

    public User registerPushToken(String workosId, String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new BadRequestException("Token is required");
        }
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (!user.getPushTokens().contains(token)) {
            user.getPushTokens().add(token);
            user = userRepository.save(user);
        }
        return user;
    }

    public void deleteUserAndData(String workosId) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        String userId = user.getId();

        List<Workout> workouts = workoutRepository.findAllByUserId(userId);
        List<String> templateIds = workouts.stream()
                .map(Workout::getTemplateId)
                .filter(id -> id != null && !id.isBlank())
                .toList();

        // Delete user-scoped records first
        workoutRecordRepository.deleteByUserId(userId);
        exerciseRecordRepository.deleteByUserId(userId);
        exercisePersonalBestRepository.deleteByUserId(userId);
        routineRepository.deleteByUserId(userId);

        // Delete workouts and their templates
        workoutRepository.deleteByUserId(userId);
        if (!templateIds.isEmpty()) {
            workoutTemplateRepository.deleteAllById(templateIds);
        }

        // Finally, delete the user document
        userRepository.deleteById(user.getId());
        workosService.deleteUser(workosId);
    }
}
