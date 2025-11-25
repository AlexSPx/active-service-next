package com.services.active.services;

import com.services.active.models.user.User;
import com.services.active.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class StreakReminderScheduler {

    private final UserRepository userRepository;
    private final ExpoPushNotificationService pushService;
    private Clock clock = Clock.systemUTC();

    @Autowired
    StreakReminderScheduler(UserRepository userRepository, ExpoPushNotificationService pushService) { // package-private
        this.userRepository = userRepository;
        this.pushService = pushService;
    }

    void setClock(Clock clock) {
        if (clock != null) this.clock = clock;
    }

    // Run at second 0 every hour
    @Scheduled(cron = "0 0 * * * *")
    public void processHourlyReminders() {
        Instant now = Instant.now(clock);
        Set<String> allZones = ZoneId.getAvailableZoneIds();

        for (String zoneId : allZones) {
            try {
                ZoneId zone = ZoneId.of(zoneId);
                ZonedDateTime zdt = ZonedDateTime.ofInstant(now, zone);

                // FORCE the search string to be "HH:00"
                // Even if the job runs at 09:00:05, this ensures we look for "09:00"
                String targetLocalTime = String.format("%02d:00", zdt.getHour());

                // Database Query (Unchanged - Exact Match)
                // Finds users in 'Europe/London' with '14:00' in their list
                List<User> usersToNotify = userRepository.findUsersToNotify(zoneId, targetLocalTime);

                if (!usersToNotify.isEmpty()) {
                    log.info("Sending notifications to {} users in {} at {}", usersToNotify.size(), zoneId, targetLocalTime);
                    pushService.sendStreakReminder(usersToNotify);
                }

            } catch (Exception e) {
                log.error("Error processing zone {}", zoneId, e);
            }
        }
    }
}
