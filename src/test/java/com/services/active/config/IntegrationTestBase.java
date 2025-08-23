package com.services.active.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

@Testcontainers
public abstract class IntegrationTestBase {

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");

    private static final String TEST_DB = "active_test";

    @BeforeAll
    static void startContainer() {
        MONGO.start();
    }

    @AfterAll
    static void stopContainer() {
        MONGO.stop();
    }

    @AfterEach
    void cleanUpDatabase() throws IOException, InterruptedException {
        MONGO.execInContainer("mongosh", "--eval", "db.getSiblingDB('" + TEST_DB + "').dropDatabase()");
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        // Ensure the Spring app uses the same DB name as cleanup
        registry.add("spring.data.mongodb.uri", () -> {
            String rsUrl = MONGO.getReplicaSetUrl();
            // Replace default '/test' database in URL with '/active_test'
            String updated = rsUrl.replace("/test?", "/" + TEST_DB + "?");
            if (updated.equals(rsUrl)) {
                updated = rsUrl.endsWith("/test") ? rsUrl.substring(0, rsUrl.length() - 4) + TEST_DB : rsUrl;
            }
            return updated;
        });
        registry.add("jwt.secret", () -> "really_long_secret_that_is_at_least_256_bits_long");
    }
}
