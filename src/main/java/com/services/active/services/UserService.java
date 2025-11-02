package com.services.active.services;

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
}
