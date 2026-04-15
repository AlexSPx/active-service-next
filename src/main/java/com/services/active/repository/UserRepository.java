package com.services.active.repository;

import com.services.active.models.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByWorkosId(String workosId);
    List<User> findByTimezoneIn(List<String> timezones);

    @Query("""
        SELECT u FROM User u
        JOIN UserNotificationSchedule s ON s.userId = u.id
        WHERE u.timezone = :timezone
          AND u.emailNotificationsEnabled = true
          AND CAST(s.scheduleTime AS string) = :localTime
    """)
    List<User> findUsersToNotify(String timezone, String localTime);
}
