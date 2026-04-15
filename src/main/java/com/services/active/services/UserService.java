package com.services.active.services;

import com.services.active.dto.UpdateUserRequest;
import com.services.active.exceptions.BadRequestException;
import com.services.active.exceptions.NotFoundException;
import com.services.active.models.Workout;
import com.services.active.models.user.FullUser;
import com.services.active.models.user.User;
import com.services.active.models.user.UserPushToken;
import com.services.active.models.user.UserNotificationSchedule;
import com.services.active.models.user.WorkOSUser;
import com.services.active.repository.*;
import com.workos.usermanagement.builders.UpdateUserOptionsBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserPushTokenRepository pushTokenRepository;
    private final UserNotificationScheduleRepository notificationScheduleRepository;
    private final UserWeeklyCompletedWorkoutRepository weeklyCompletedRepository;
    private final StreakService streakService;
    private final WorkoutRepository workoutRepository;
    private final WorkoutTemplateRepository workoutTemplateRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final ExercisePersonalBestRepository exercisePersonalBestRepository;
    private final RoutineRepository routineRepository;
    private final RoutinePatternRepository routinePatternRepository;

    private final WorkosService workosService;

    public FullUser getUserById(String workosId) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        streakService.checkStreak(user);

        WorkOSUser workOSUser = workosService.getUser(workosId);

        return FullUser.from(user, workOSUser);
    }

    @Transactional
    public FullUser updateUser(String workosId, UpdateUserRequest request) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        UpdateUserOptionsBuilder workosUpdateBuilder = UpdateUserOptionsBuilder.create(workosId);

        if (request.getFirstName() != null) {
            workosUpdateBuilder.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            workosUpdateBuilder.setLastName(request.getLastName());
        }
        if (request.getUsername() != null) {
            user.setUsername(request.getUsername());
        }
        if (request.getRegistrationCompleted() != null) {
            user.setRegistrationCompleted(request.getRegistrationCompleted());
        }
        if (request.getNotificationFrequency() != null) {
            updateNotificationPreferences(user, request.getNotificationFrequency());
        }
        if (request.getTimezone() != null) {
            String tz = request.getTimezone();
            try {
                ZoneId.of(tz);
            } catch (Exception e) {
                throw new BadRequestException("Invalid timezone. Use an IANA identifier like 'Europe/Sofia'.");
            }
            user.setTimezone(tz);
        }
        if (request.getMeasurements() != null) {
            var mReq = request.getMeasurements();
            Double newWeight = mReq.getWeightKg();
            Integer newHeight = mReq.getHeightCm();
            if (newWeight != null && newWeight <= 0) {
                throw new BadRequestException("weightKg must be > 0");
            }
            if (newHeight != null && newHeight <= 0) {
                throw new BadRequestException("heightCm must be > 0");
            }
            if (newWeight != null) user.setWeightKg(newWeight);
            if (newHeight != null) user.setHeightCm(newHeight);
        }

        WorkOSUser workOSUser = workosService.updateUser(workosId, workosUpdateBuilder.build());
        User dbUser = userRepository.save(user);

        return FullUser.from(dbUser, workOSUser);
    }

    @Transactional
    public User registerPushToken(String workosId, String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new BadRequestException("Token is required");
        }
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (!pushTokenRepository.existsByUserIdAndToken(user.getId(), token)) {
            pushTokenRepository.save(UserPushToken.builder()
                    .userId(user.getId())
                    .token(token)
                    .build());
        }
        return user;
    }

    @Transactional
    public void deleteUserAndData(String workosId) {
        User user = userRepository.findByWorkosId(workosId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        UUID userId = user.getId();

        List<Workout> workouts = workoutRepository.findAllByUserId(userId);
        List<UUID> templateIds = workouts.stream()
                .map(Workout::getTemplateId)
                .filter(id -> id != null)
                .toList();

        // Delete user-scoped records first
        exerciseRecordRepository.deleteByUserId(userId);
        workoutRecordRepository.deleteByUserId(userId);
        exercisePersonalBestRepository.deleteByUserId(userId);
        routinePatternRepository.deleteByRoutineId(userId); // patterns cascade via routine delete
        routineRepository.deleteByUserId(userId);
        weeklyCompletedRepository.deleteByUserId(userId);
        pushTokenRepository.deleteByUserId(userId);
        notificationScheduleRepository.deleteByUserId(userId);

        // Delete workouts and their templates
        workoutRepository.deleteByUserId(userId);
        if (!templateIds.isEmpty()) {
            for (UUID templateId : templateIds) {
                // Template exercises cascade via ON DELETE CASCADE
                workoutTemplateRepository.deleteById(templateId);
            }
        }

        // Finally, delete the user
        userRepository.deleteById(userId);
        workosService.deleteUser(workosId);
    }

    private void updateNotificationPreferences(User user, Integer notificationFrequency) {
        if (notificationFrequency == null) return;

        // Clear existing schedule
        notificationScheduleRepository.deleteByUserId(user.getId());

        if (notificationFrequency <= 0) {
            user.setEmailNotificationsEnabled(false);
        } else {
            user.setEmailNotificationsEnabled(true);
            LocalTime startTime = LocalTime.of(9, 0);
            LocalTime endTime = LocalTime.of(21, 0);

            List<LocalTime> schedule = new ArrayList<>();
            if (notificationFrequency == 1) {
                schedule.add(startTime);
            } else {
                long totalMinutes = java.time.temporal.ChronoUnit.MINUTES.between(startTime, endTime);
                long intervalMinutes = totalMinutes / (notificationFrequency - 1);
                for (int i = 0; i < notificationFrequency; i++) {
                    schedule.add(startTime.plusMinutes(intervalMinutes * i));
                }
            }
            for (LocalTime time : schedule) {
                notificationScheduleRepository.save(UserNotificationSchedule.builder()
                        .userId(user.getId())
                        .scheduleTime(time)
                        .build());
            }
        }
    }
}
