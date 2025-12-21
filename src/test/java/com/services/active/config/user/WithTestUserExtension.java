package com.services.active.config.user;

import com.services.active.models.user.User;
import com.services.active.repository.UserRepository;
import org.junit.jupiter.api.extension.*;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.time.LocalDate;
import java.util.Base64;

public class WithTestUserExtension implements BeforeEachCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(WithTestUserExtension.class);

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
        if (this.userRepository != null) return;
        try {
            ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
            if (this.userRepository == null) this.userRepository = applicationContext.getBean(UserRepository.class);
        } catch (Exception ignored) {
            try {
                if (this.userRepository == null) {
                    Field field = testInstance.getClass().getDeclaredField("userRepository");
                    field.setAccessible(true);
                    this.userRepository = (UserRepository) field.get(testInstance);
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalStateException("UserRepository not found for WithTestUserExtension", e);
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

        String email = annotation.email();
        String workosId = "test_workos_" + email.replace("@", "_").replace(".", "_");

        // Check if user already exists by workosId
        this.user = userRepository.findByWorkosId(workosId).orElseGet(() -> {
            // Create new test user directly in database with mock WorkOS ID
            User newUser = User.builder()
                    .workosId(workosId)
                    .createdAt(LocalDate.now())
                    .timezone("UTC")
                    .build();
            return userRepository.save(newUser);
        });

        context.getStore(NAMESPACE).put("user", this.user);

        // Generate a properly formatted mock JWT token
        // Format: header.payload.signature (all base64 encoded)
        // The TestSecurityConfig's mock JwtDecoder will decode this
        this.token = createMockJwt(this.user.getWorkosId());
        context.getStore(NAMESPACE).put("jwt", this.token);

        System.out.println("========================================");
        System.out.println("DEBUG: Creating test user context");
        System.out.println("DEBUG: Email: " + email);
        System.out.println("DEBUG: WorkosId: " + workosId);
        System.out.println("DEBUG: User ID: " + this.user.getId());
        System.out.println("DEBUG: Generated token: " + this.token);
        System.out.println("========================================");
    }

    /**
     * Create a mock JWT token in proper format: header.payload.signature
     * This looks like a real JWT so Spring Security won't reject it as malformed
     */
    private String createMockJwt(String workosId) {
        // Create a simple header (algorithm and type)
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";

        // Create a simple payload with the workosId as subject
        long now = System.currentTimeMillis() / 1000;
        long exp = now + 3600; // 1 hour expiry
        String payload = String.format("{\"sub\":\"%s\",\"iat\":%d,\"exp\":%d}", workosId, now, exp);

        // Create a mock signature (doesn't need to be valid, just present)
        String signature = "mock-signature-for-testing";

        // Base64 encode each part (without padding to match JWT format)
        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes());
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
        String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signature.getBytes());

        // Combine into JWT format
        return encodedHeader + "." + encodedPayload + "." + encodedSignature;
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
