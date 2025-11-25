package com.services.active.config.user;

import com.services.active.dto.AuthRequest;
import com.services.active.dto.LoginRequest;
import com.services.active.dto.TokenResponse;
import com.services.active.models.user.User;
import com.services.active.repository.UserRepository;
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
    private UserRepository userRepository;
    private String token;
    private User user;

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        Object testInstance = context.getRequiredTestInstance();
        new TestContextManager(testInstance.getClass()).prepareTestInstance(testInstance);

        // Try to get beans now; if it fails, resolve lazily later
        resolveBeans(context, testInstance);

        // Attempt eager creation to cache both token and user
        createContextIfAnnotated(context);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Parameter parameter = parameterContext.getParameter();
        if (!parameter.isAnnotationPresent(TestUserContext.class)) return false;
        Class<?> type = parameter.getType();
        return type.equals(String.class) || type.equals(User.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext context) {
        // Ensure context is prepared
        createContextIfAnnotated(context);

        Class<?> type = parameterContext.getParameter().getType();
        if (type.equals(String.class)) {
            String cached = context.getStore(NAMESPACE).get("jwt", String.class);
            if (cached != null) return cached;
            if (this.token != null) {
                context.getStore(NAMESPACE).put("jwt", this.token);
                return this.token;
            }
        } else if (type.equals(User.class)) {
            User cachedUser = context.getStore(NAMESPACE).get("user", User.class);
            if (cachedUser != null) return cachedUser;
            if (this.user != null) {
                context.getStore(NAMESPACE).put("user", this.user);
                return this.user;
            }
        }
        throw new IllegalStateException("@WithTestUser not present on method or class, cannot provide @TestUserContext");
    }

    private void resolveBeans(ExtensionContext context, Object testInstance) {
        if (this.authService != null && this.userRepository != null) return;
        try {
            ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
            if (this.authService == null) this.authService = applicationContext.getBean(AuthService.class);
            if (this.userRepository == null) this.userRepository = applicationContext.getBean(UserRepository.class);
        } catch (Exception ignored) {
            try {
                if (this.authService == null) {
                    Field field = testInstance.getClass().getDeclaredField("authService");
                    field.setAccessible(true);
                    this.authService = (AuthService) field.get(testInstance);
                }
                if (this.userRepository == null) {
                    Field field = testInstance.getClass().getDeclaredField("userRepository");
                    field.setAccessible(true);
                    this.userRepository = (UserRepository) field.get(testInstance);
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalStateException("Required beans not found for WithTestUserExtension", e);
            }
        }
    }

    private void createContextIfAnnotated(ExtensionContext context) {
        // if already cached, skip
        String cachedToken = context.getStore(NAMESPACE).get("jwt", String.class);
        User cachedUser = context.getStore(NAMESPACE).get("user", User.class);
        if (cachedToken != null && cachedUser != null) {
            this.token = cachedToken;
            this.user = cachedUser;
            return;
        }

        WithTestUser annotation = findWithTestUserAnnotation(context);
        if (annotation == null) return;

        // Ensure beans present
        resolveBeans(context, context.getRequiredTestInstance());

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

        // Fetch user entity
        this.user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Failed to load test user by email"));
        context.getStore(NAMESPACE).put("user", this.user);

        // Login to get token
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword(password);
        TokenResponse tokenResponse = authService.login(login);
        if (tokenResponse == null || tokenResponse.getToken() == null) {
            throw new IllegalStateException("Failed to obtain JWT token in WithTestUserExtension");
        }
        this.token = tokenResponse.getToken();
        context.getStore(NAMESPACE).put("jwt", this.token);
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
