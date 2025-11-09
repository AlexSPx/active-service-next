package com.services.active.services;

import com.services.active.models.User;
import com.services.active.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class StreakReminderScheduler {

    private final UserRepository userRepository;
    private final ExpoPushNotificationService pushService;
    private Clock clock = Clock.systemUTC();

    @Autowired
    public StreakReminderScheduler(UserRepository userRepository,
                                   ExpoPushNotificationService pushService) {
        this.userRepository = userRepository;
        this.pushService = pushService;
    }

    // Optional bean injection
    @Autowired(required = false)
    public void setClock(Clock clock) {
        if (clock != null) this.clock = clock;
    }

    // Run at second 0 every hour
    @Scheduled(cron = "0 0 * * * *")
    public void send9amLocalReminders() {
        Instant now = Instant.now(clock);
        Set<String> allZones = ZoneId.getAvailableZoneIds();
        List<String> zonesAtNine = new ArrayList<>();

        for (String zoneId : allZones) {
            try {
                ZoneId zone = ZoneId.of(zoneId);
                ZonedDateTime zdt = ZonedDateTime.ofInstant(now, zone);
                // At top of the hour; match when local hour is 9
                if (zdt.getHour() == 9) {
                    zonesAtNine.add(zoneId);
                }
            } catch (Exception e) {
                // skip invalid zone id (should not happen with availableZoneIds)
            }
        }

        if (zonesAtNine.isEmpty()) {
            return;
        }

        List<User> users = userRepository.findByTimezoneIn(zonesAtNine);
        if (users.isEmpty()) return;

        // Bulk send; service handles filtering users without tokens
        pushService.sendStreakReminder(users);
    }
}
