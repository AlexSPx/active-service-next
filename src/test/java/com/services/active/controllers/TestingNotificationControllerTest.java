package com.services.active.controllers;

import com.services.active.services.ExpoPushNotificationService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestingNotificationControllerTest {

    @Mock
    private ExpoPushNotificationService pushNotificationService;

    @ParameterizedTest
    @ValueSource(strings = {"local", "test", "local,test"})
    void controller_withDevelopmentProfile_isAvailable(String profiles) {
        contextWithProfiles(profiles).run(context ->
                assertThat(context).hasSingleBean(TestingNotificationController.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "docker", "prod", "local,prod", "test,prod"})
    void controller_withoutDevelopmentProfileOrWithProduction_isUnavailable(String profiles) {
        contextWithProfiles(profiles).run(context ->
                assertThat(context).doesNotHaveBean(TestingNotificationController.class));
    }

    private ApplicationContextRunner contextWithProfiles(String profiles) {
        return new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=" + profiles)
                .withBean(ExpoPushNotificationService.class, () -> pushNotificationService)
                .withUserConfiguration(TestingNotificationController.class);
    }
}
