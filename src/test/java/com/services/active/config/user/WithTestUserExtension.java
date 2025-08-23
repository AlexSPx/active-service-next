package com.services.active.config.user;

import com.services.active.dto.AuthRequest;
import com.services.active.dto.LoginRequest;
import com.services.active.dto.TokenResponse;
import com.services.active.services.AuthService;
import org.junit.jupiter.api.extension.*;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;

public class WithTestUserExtension implements BeforeEachCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(WithTestUserExtension.class);

    private AuthService authService;
    private String token;

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        Object testInstance = context.getRequiredTestInstance();
        new TestContextManager(testInstance.getClass()).prepareTestInstance(testInstance);

        // Try to get AuthService now; if it fails, resolve lazily later
        resolveAuthService(context, testInstance);

        // Attempt eager token creation; if it fails due to order, resolve lazily in resolveParameter
        this.token = createTokenIfAnnotated(context);
        if (this.token != null) {
            context.getStore(NAMESPACE).put("jwt", this.token);
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Parameter parameter = parameterContext.getParameter();
        return parameter.getType().equals(String.class) && parameter.isAnnotationPresent(TestUserToken.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext context) {
        String cached = context.getStore(NAMESPACE).get("jwt", String.class);
        if (cached != null) {
            return cached;
        }
        if (this.token != null) {
            context.getStore(NAMESPACE).put("jwt", this.token);
            return this.token;
        }
        // Lazily resolve AuthService and create token now
        resolveAuthService(context, context.getRequiredTestInstance());
        String created = createTokenIfAnnotated(context);
        if (created == null) {
            throw new IllegalStateException("@WithTestUser not present on method or class, cannot provide @TestUserToken");
        }
        context.getStore(NAMESPACE).put("jwt", created);
        this.token = created;
        return created;
    }

    private void resolveAuthService(ExtensionContext context, Object testInstance) {
        if (this.authService != null) return;
        try {
            ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
            this.authService = applicationContext.getBean(AuthService.class);
        } catch (Exception ignored) {
            try {
                Field field = testInstance.getClass().getDeclaredField("authService");
                field.setAccessible(true);
                this.authService = (AuthService) field.get(testInstance);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalStateException("AuthService bean not found for WithTestUserExtension", e);
            }
        }
    }

    private String createTokenIfAnnotated(ExtensionContext context) {
        WithTestUser annotation = findWithTestUserAnnotation(context);
        if (annotation == null) return null;

        String username = annotation.username();
        String email = annotation.email();
        String password = annotation.password();

        AuthRequest signup = new AuthRequest();
        signup.setUsername(username);
        signup.setEmail(email);
        signup.setFirstName("Test");
        signup.setLastName("User");
        signup.setPassword(password);
        try {
            authService.signup(signup);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() != 409) {
                throw e;
            }
            // ignore existing user
        }

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword(password);
        TokenResponse tokenResponse = authService.login(login);
        if (tokenResponse == null || tokenResponse.getToken() == null) {
            throw new IllegalStateException("Failed to obtain JWT token in WithTestUserExtension");
        }
        return tokenResponse.getToken();
    }

    private WithTestUser findWithTestUserAnnotation(ExtensionContext context) {
        AnnotatedElement element = context.getElement().orElse(null);
        if (element != null) {
            WithTestUser ann = element.getAnnotation(WithTestUser.class);
            if (ann != null) return ann;
        }
        Class<?> testClass = context.getRequiredTestClass();
        return testClass.getAnnotation(WithTestUser.class);
    }
}
