package com.services.active.repository;

import com.services.active.models.user.UserPushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPushTokenRepository extends JpaRepository<UserPushToken, UUID> {
    List<UserPushToken> findAllByUserId(UUID userId);
    Optional<UserPushToken> findByUserIdAndToken(UUID userId, String token);
    boolean existsByUserIdAndToken(UUID userId, String token);
    void deleteByUserId(UUID userId);
}
