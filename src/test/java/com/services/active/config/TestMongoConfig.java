package com.services.active.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MongoDBContainer;

@TestConfiguration
public class TestMongoConfig {

    @Bean
    @ServiceConnection
    public static MongoDBContainer mongoContainer() {
        return new MongoDBContainer("mongo:7.0.14");
    }
}

