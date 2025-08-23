package com.services.active.config.user;

import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(WithTestUserExtension.class)
public @interface WithTestUser {
    String username() default "testuser";
    String email() default "testuser@example.com";
    String password() default "StrongP@ssw0rd";
}

