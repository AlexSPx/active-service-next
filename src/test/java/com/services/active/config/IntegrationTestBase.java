package com.services.active.config;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

@Import(TestMongoConfig.class)
public abstract class IntegrationTestBase {

    @Autowired
    private MongoTemplate mongoTemplate;

    @AfterEach
    void cleanUpDatabase() {
        // Drop the configured test database after each test to avoid cross-test conflicts
        mongoTemplate.getDb().drop();
    }
}
