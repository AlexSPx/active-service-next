package com.services.active.config.user;

import com.services.active.dto.AuthRequest;
import com.services.active.dto.LoginRequest;
import com.services.active.dto.TokenResponse;
import com.services.active.services.AuthService;
import org.junit.jupiter.api.extension.*;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Parameter;

public class WithTestUserExtension implements BeforeEachCallback, ParameterResolver {
    private AuthService authService;
    private String token;

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        Object testInstance = context.getRequiredTestInstance();
        new TestContextManager(testInstance.getClass()).prepareTestInstance(testInstance);

        // Prefer getting AuthService from Spring ApplicationContext
        try {
            ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
            authService = applicationContext.getBean(AuthService.class);
        } catch (Exception ignored) {
            // Fallback to reflection on the test instance if necessary
            try {
                Field field = testInstance.getClass().getDeclaredField("authService");
                field.setAccessible(true);
                authService = (AuthService) field.get(testInstance);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalStateException("AuthService bean not found for WithTestUserExtension", e);
            }
        }

        WithTestUser annotation = context.getElement()
                .flatMap(el -> java.util.Optional.ofNullable(el.getAnnotation(WithTestUser.class)))
                .orElse(null);
        if (annotation != null) {
            String username = annotation.username();
            String email = annotation.email();
            String password = annotation.password();

            AuthRequest signup = new AuthRequest();
            signup.setUsername(username);
            signup.setEmail(email);
            signup.setFirstName("Test");
            signup.setLastName("User");
            signup.setPassword(password);
            authService.signup(signup).block();

            LoginRequest login = new LoginRequest();
            login.setEmail(email);
            login.setPassword(password);
            TokenResponse tokenResponse = authService.login(login).block();
            if (tokenResponse == null || tokenResponse.getToken() == null) {
                throw new IllegalStateException("Failed to obtain JWT token in WithTestUserExtension");
            }
            token = tokenResponse.getToken();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Parameter parameter = parameterContext.getParameter();
        return parameter.getType().equals(String.class) && parameter.isAnnotationPresent(TestUserToken.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return token;
    }
}
