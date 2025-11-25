package com.services.active.repository;

import com.services.active.models.user.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    List<User> findByTimezoneIn(List<String> timezones);

    @Query("{ 'timezone': ?0, 'notificationPreferences.emailNotificationsEnabled': true, 'notificationPreferences.schedule': ?1 }")
    List<User> findUsersToNotify(String timezone, String localTime);
}
