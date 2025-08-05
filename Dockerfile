# =====================================================
# SUREVOTE — Multi-Stage Dockerfile
# Stage 1: Build with Maven
# Stage 2: Run with lean Eclipse Temurin JRE 17
# =====================================================

# ─────────────────────────────────────────────────────
# STAGE 1 — Build
# ─────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy dependency manifests first (Docker layer cache optimization)
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw

# Download all dependencies (cached as a separate layer)
RUN ./mvnw dependency:go-offline -q

# Copy source code and build the final JAR
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ─────────────────────────────────────────────────────
# STAGE 2 — Runtime
# ─────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Security: run as a non-root user
RUN addgroup -S surevote && adduser -S surevote -G surevote

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/surevote-*.jar app.jar

# Create upload directory and set ownership
RUN mkdir -p /app/uploads && chown -R surevote:surevote /app

USER surevote

# Expose the application port
EXPOSE 8080

# JVM tuning for containerized environment
ENV JAVA_OPTS="-Xms256m -Xmx512m \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -Djava.security.egd=file:/dev/./urandom"

# Health check — uses the Spring Boot actuator /health endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
