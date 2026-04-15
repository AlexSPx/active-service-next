package com.services.active.repository;

import com.services.active.models.user.UserNotificationSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserNotificationScheduleRepository extends JpaRepository<UserNotificationSchedule, UUID> {
    List<UserNotificationSchedule> findAllByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
