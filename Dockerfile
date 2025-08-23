# ✅ Build with Maven + Java 21
FROM maven:3.9.10-eclipse-temurin-24-alpine AS builder
# Set working directory
WORKDIR /app

# Copy everything
COPY . .

# Build the web module and skip tests
RUN mvn clean install -DskipTests

# Use a minimal JRE image for Java 24
FROM eclipse-temurin:24-jre-alpine
# Set working directory
WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder /app/target/active-*.jar app.jar

# Default to the docker profile at runtime; override via env if needed
ENV SPRING_PROFILES_ACTIVE=docker

# Expose Spring Boot's default port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
